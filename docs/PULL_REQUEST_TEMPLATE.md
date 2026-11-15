## Details

*Summary of pull request, including references to relevant tickets (if applicable).*

## Testing

*Besides running unit tests, how can the reviewer test your feature / bug fix? Are there edge cases to be aware of?*

## Checklist

Before your pull request can be approved, be sure to check all boxes:

- [ ] Targets the `dev` branch (not `master`)
- [ ] Passing unit test for new or updated code (if applicable)
- [ ] Passes all tests: the GitHub Actions checks ("InterMine CI" and "Checkstyle") are green, and you ran the affected builds locally (for example `./gradlew build` in `intermine/` or `bio/`)
- [ ] Documentation (if applicable)
- [ ] Single purpose
- [ ] Detailed commit messages
- [ ] Well commented code
- [ ] Checkstyle (`./gradlew checkstyleMain`, which `./gradlew build` also runs)
