sirfrancis
==========

*NOTE: Some files (notably build.gradle) have no commit history, they were cleaned of private data using [bfg-repo-cleaner](https://rtyley.github.io/bfg-repo-cleaner/). This repository started out private, and I decided to share the code, so I had to remove several files from the history before making it public.*

SirFrancis is my project to create a lightweight movie-recommendation engine. The data is parsed into the database by an automated reproducible process which downloads bulk exports from [The OMDB API](http://www.omdbapi.com/). This process takes atomic movie listings, deduplicates actor, director and writer entries, and connects them all in an OrientDB graph database which allows for faster graph traversals than in a traditional relational database. Users can then add ratings for movies they like (or don't) and these are then filtered against the overall movie graph to generate potential recommendations based on a process similar to collaborative filtering. However, no user data is required to bootstrap these recommendations, as in traditional collaborative filtering processes. This recommendation process is based on the structure of the graph itself, as opposed to preferences expressed by other users.

Originally I had intended to deploy this as a side project and try to find ways to monetize it, but now I'm just interested in sharing what a Dropwizard + OrientDB app might look like. I intend to eventually complement this back-end work with a front-end, but for now SirFrancis can only serve data over HTTP requests.

SirFrancis' backend is built on Java 8 and Gradle using the following technologies:

* [Dropwizard 0.8.0](http://www.dropwizard.io/)
* [OrientDB 2.1](http://orientdb.com/docs/last/)
* [SendGrid](https://sendgrid.com/)

##Running SirFrancis

If you'd like to try it out:

1. Clone the repo
2. Edit `config.yml.example` to fill in the stripped-out fields
  * `omdb-api-key`: Received after donating to the OMDB API project.
  * `omdb-download-url`: The URL where the Movies database download comes from. Retrievable from your browser's download manager after downloading the movie database dump.
  * `sendgrid-username`: Your SendGrid username. SendGrid is apparently in the process of changing to key-based auth, but this works for now.
  * `sendgrid-username`: Your SendGrid password.
3. Further edit `config.yml.example` to match your environment. Note that `config.yml.example` is coded with Linux paths by default. Whatever path you choose, make sure it's owned by the account you run Java with. 
4. Rename `config.yml.example` to `config.yml` and leave it in the project root.
5. Ensure that port 8081 is firewalled. Dropwizard by default accepts unauthenticated admin requests over this port.
6. Execute `gradle runServer`, this will build the fat JAR and run the server with `config.yml`.

##Using SirFrancis

###Admin HTTP endpoints

There are a number of server-side administrative URL endpoints for updating the database, running healthchecks, etc. Dropwizard listens for POST requests to these endpoints on port 8081 by default.

* `POST    /tasks/download-omdb` will download the most up-to-date monthly OMDB database dump, and store it in the directory configured in `config.yml`. It will also back up any previous database dump that may have been "current" previously.
* `POST    /tasks/update-db` will take the most recent database dump in the directory configured in `config.yml`, and will parse that to either bootstrap or update the database. This operation is more-or-less idempotent, and can be run multiple times. After parsing the database dump, this process will then clean the graph, removing out of date movies (those that are no longer contained in the dump files), and deleting any orphaned nodes (movies that have no actors, directors, or writers).

###Client HTTP endpoints

These are the client-facing URL endpoints to use, and are by default listened to on port 8080. They mostly require basic HTTP auth headers. I run SirFrancis behind an SSL-terminating nginx proxy, so the plaintext auth is always over TLS. All of these endpoints are listed assuming that you'll prefix them with the URL of the host.

####To create a new account:

No authentication required.

1. `POST  /user/create/new/{email}/{password}`
2. `GET  /user/create/confirm/{email}/{confirmationKey}`
  * `confirmationKey`: Received in email.
  
####Finding Movie IDs to Rate:

Requires auth:
* `GET  /search/movies/{query}/{numResults}`
  * `query`: full text query
  * `numResults`: limit number of results

####Rating Movies

* To view all of your ratings, auth required: `GET  /ratings/`
* To add a rating, 1-10, auth required: `POST  /ratings/add/{imdbID}/{rating}`
  * `imdbID`: This field is returned in the search results. Can also be found in the URL of any IMDB page.
  * `rating`: valid values are 1-10.
* To ignore a movie that you haven't seen (i.e. don't recommend it anymore): `POST  /ratings/ignore/{imdbID}`
  * `imdbID`: This field is returned in the search results. Can also be found in the URL of any IMDB page.

####Viewing Recommendations

The whole reason for creating SirFrancis! Best when an account already has >20 ratings. Auth required.

`GET  /recommendations/{numReturned}`

####Change password:

1. Forgot password, no auth required: `POST  /user/password/forgot/{email}/`
2. Change password, no auth required: `POST  /user/password/change/{email}/{newPassword}/{confirmKey}`
  * `confirmKey`: Received in email.

####Other user account endpoints:

* Check if a user exists, no auth required (for a hypothetical login screen): `GET  /user/exists/{username}`
* Delete user, auth required: `POST  /user/delete/`

##Structure

Rather than creating separate READMEs for each folder and package, this section describes how the important elements of the project are organized.

* `src/integration-test` -- separate test root for integration tests
* `src/main/java/io.sirfrancis.bacon/api.responses`
  * POJOs that are just for responses. You can craft custom responses, but it's much easier if you just return a POJO.
* `src/main/java/io.sirfrancis.bacon/auth`
  * Dropwizard provides parsing functionality for authentication headers, but it's up to the app to plug it in to the DB and to validate it. This uses built-in Java crypto and stores all user passwords as a salted hash.
* `src/main/java/io.sirfrancis.bacon/core`
  * Dropwizard serializes and deserializes domain POJOs, which live here.
* `src/main/java/io.sirfrancis.bacon/db`
  * `enums`
    * Because I've written a pseudo-custom ORM-not-really, I decided to house all important string literals in this package.
  * This package is where all the bugs live! It's responsible for interacting directly with the OrientDB graph database. It's possible to use a JDBC-compatible ORM with OrientDB, but that defeats the purpose of using a graph database which looks up relationships in O(1).
* `src/main/java/io.sirfrancis.bacon/health`
  * Database health check is it for now, and it's pretty rudimentary.
* `src/main/java/io.sirfrancis.bacon/mailers`
  * These classes are meant to encapsulate some of the SendGrid functionality to allow a change to a different email service. Need to make a couple changes still.
* `src/main/java/io.sirfrancis.bacon/resources`
  * All URL endpoints are defined here. I've done a fair amount to contain the business logic here, but the DB layer still has a bit.
* `src/main/java/io.sirfrancis.bacon/tasks`
  * The two tasks here are used to initialize and update the content in the database.
* `src/main/java/io.sirfrancis.bacon/util`
  * Could also be called "misc" -- this just has a few things that are used in multiple packages.
* `src/main/java/io.sirfrancis.bacon/BaconApplication.java`
  * This is the main application class, and it spins up everything that will be called.
* `src/main/java/io.sirfrancis.bacon/BaconConfiguration.java`
  * This object is used by Dropwizard to serialize the config.yml file.
* `src/main/resources`
  * Dropwizard is awesome in many ways, one of which is that it automatically inserts this banner at the top of your logs and console.
* `build.gradle`
  * Nothing too fancy here. A few custom tasks. Uses the capsule plugin (https://github.com/danthegoodman/gradle-capsule-plugin) for creating a fat JAR.
* `config.yml.example`
  * A version of the config file with credentials stripped out.

##Running SirFrancis's Tests

There are two Gradle tasks (run `clean` first): `test` and `integrationTest`. Tests are very limited coverage right now. *TODO!*
