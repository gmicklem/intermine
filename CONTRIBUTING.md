# Contributing to InterMine projects

Welcome, we're happy to have you here! 🎉 

Everyone is welcome to participate and work in issues on all InterMine repos. 

Please visit our [contributing guidelines](http://intermine.org/contributing/) for more info. 

InterMine has a [Code of Conduct](http://intermine.org/code-of-conduct/), which we expect all participants to abide by. Generally, be nice and respectful to one another!

## Pull requests for this repository

- Branch from `dev` and open your pull request against `dev`. `master` only receives releases.
- Keep each pull request to a single purpose, with detailed commit messages.
- Build with JDK 17 (JDK 8 also works until 6.0.0 is released), see [docs/BUILDING.md](docs/BUILDING.md).
- Before you push, run Checkstyle and the tests for the builds you changed. The back end is a set of separate Gradle builds (`plugin`, `intermine`, `bio`, `bio/sources`, `bio/postprocess`, `bio/postprocess-test`, `testmine`). From the build's directory:

  ```bash
  ./gradlew checkstyleMain   # Checkstyle only (build runs it too)
  ./gradlew checkThrowsDocs  # a checked exception in a `throws` clause needs a Javadoc `@throws` tag (CI runs it with Checkstyle)
  ./gradlew build            # compile, Checkstyle and unit tests
  ```

  `config/ci/init.sh` and `config/ci/run.sh` show exactly what the continuous integration does, including the database setup the unit tests need.
- GitHub Actions runs "InterMine CI" (unit tests, and the web service tests against the JavaScript and Python clients) and "Checkstyle" on every pull request. Both must be green before review.
- Dependency updates arrive from Dependabot (weekly); review them like any other pull request.
