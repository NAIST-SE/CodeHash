# CLAUDE.md

CodeHash computes source file similarity using N-gram multisets of tokens (ignoring whitespace and comments) and b-bit minhash.
See README.md for the command line tools and their output formats.

## Build and test

Use Maven.  The project targets Java 11 (`<release>11</release>` in pom.xml); do not use APIs newer than Java 11.

Commands:
- `mvn test` runs JUnit 4 tests in `src/test/java`.
- `mvn package` creates `target/CodeHash-[VERSION].jar` and `target/CodeHash-[VERSION]-jar-with-dependencies.jar`.

## Source layout

- `src/main/java`: Java source code.
- `src/main/antlr4`: ANTLR lexer grammars.  `antlr4-maven-plugin` generates lexers into the `jp.naist.se.commentlister.lexer` package (under `target/generated-sources`); do not edit generated files.
- `src/main/ruby/processruby.rb`: A separated program for Ruby files.
- `src/test/java`: Tests.  Test classes are placed in the same package as the classes under test.
- `src/test/resources/c-sample`: Sample C files used by `DirectComparisonMainTest` and the README example.

## Packages

All packages are under `jp.naist.se.codehash`.

- (root) `jp.naist.se.codehash`: Main classes for files and git repositories.
  - `FileCodeHash`: Computes hashes and 1-bit minhash vectors of files on disk.
  - `GitCodeHash`: Computes hashes of blobs in git repositories, given a CSV of file lists or a repository directory.
  - `GitFileList`: Lists non-binary blobs reachable from HEAD.  `visitBlobs` is also used by `GitCodeHash`.
- `comparison`: Main classes to compare files.
  - `ComparisonMain`: Estimates similarity from minhash vectors produced by `FileCodeHash` (`MinHashEntry` represents a line).
  - `DirectComparisonMain`: Compares files directly and writes JSON.  Handles `-lang`, `-group`, `-n`, `-th*`, `-filter`, `-metrics`, `-compare` options.
  - `FileTokenizerMain`: Writes tokens of files in JSON.
- `file`: Files to be compared by `DirectComparisonMain`.
  - `FileEntity`: A file with its hashes, N-gram multisets, and minhash.
  - `FileGroup`: A named group of files (`-group` option).
- `hash`: Hash computation.
  - `FileHash`: SHA-1 of a file content.
  - `SourceCodeHash`: Tokenizes a source file and computes its codehash and minhash (`MinHashType` selects None, Murmur3, SHA1, or SHA1InPaper).
  - `CodeHashTokenReader`: A token reader computing codehash (SHA-1 of tokens ignoring comments and whitespace).
  - `NgramReader`, `NgramMultiset`: N-grams of tokens.
  - `MurmurHash3`, `MurmurMinHash`: b-bit minhash using MurmurHash3.
  - `MinHashDefaults`: Shared minhash parameters.
- `sha1`: b-bit minhash using SHA-1.  `SHA1MinHashMSR2017` reproduces the computation used in the MSR 2017 paper.
- `similarity`: Similarity metrics and filters for `DirectComparisonMain`.
  - `SimilarityMetric`: Strategy interface of a metric.  `NgramSimilarityMetric` is the base class of the N-gram metrics (Jaccard, overlap coefficient, overlap similarity, each with and without identifier normalization).
  - `SimilarityMetrics`: The registry of metrics.  A new metric must be registered here to be available in `-metrics` and `-filter`.
  - `FilePair`: A pair of files; caches intermediate values shared by metrics.
  - `SimilarityRecord`: Similarity values of a pair, calculated on demand and cached.
  - `SimilarityFilter`: Parser and evaluator of `-filter` expressions.
- `token`: Tokenization.
  - `FileType`: Supported languages (CPP, JAVA, ECMASCRIPT, PYTHON, PHP, CSHARP, PLAINTEXT), mapping from language names and file extensions, and creation of token readers.  `PLAINTEXT` is never selected by a file extension.
  - `TokenReader`: Interface to read tokens.  `AntlrTokenReader` implements it with ANTLR lexers.
- `normalizer`: Identifier normalization for each language (`Normalizer` and its implementations for C/C++, Java, C#, ECMAScript, and Python).
- `util`: Utilities (`GitRepositoryUtil` to open a repository, `HashStringUtil`, `StringMultiset`, `Counter`).

`org.antlr.v4.runtime.CaseChangingCharStream` is a helper for the case-insensitive PHP lexer.
