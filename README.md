# CodeHash Tool

This tool computes source file similarity using 3-gram multiset ignoring whitespace and comments.
The tool can estimate source code siimlarity using b-bit minhash; the tool can skip unnecessary comparison if two files are unlikely similar.

The implementation is a revised version that has been used in our technical paper: 
>        Takashi Ishio, Yusuke Sakaguchi, Kaoru Ito and Katsuro Inoue: 
>        Source File Set Search for Clone-and-Own Reuse Analysis, In Proc. of MSR 2017.
>        <https://doi.org/10.1109/MSR.2017.19>


The source code is written in Java.  You can build the project using Maven.
>        mvn package

The tool requires JDK 11 or later.
The command creates `target/CodeHash-[VERSION]-jar-with-dependencies.jar` that includes all dependencies.
The command lines in this document refer to the file as `CodeHash.jar`.


For file similarity, supported programming languages are: C/C++, Java, ECMAScript, Python, PHP, and C#.
In addition, `PLAINTEXT` treats a file as plain text; it splits the content into words (`[a-zA-Z0-9_]+`) and symbols (other non-whitespace characters).  The tokenizer does not recognize comments; they are also regarded as words and symbols.
`PLAINTEXT` is not automatically selected by file extensions; it is used only when explicitly specified (e.g., the `-lang:PLAINTEXT` option or the language column of a file list).

## Extract 1-bit minhash vectors from source files

The main class `FileCodeHash` accepts file names (or directory names).
The main class reports hash values for each file with file names in addition to SHA-1 hash, code hash, and minhash. 

The following command line is an example to extract minhash values from source files in `src` directory and store them into a file.
The command uses a redirection because the tool write the result to STDOUT by default.

>        java -classpath CodeHash.jar jp.naist.se.codehash.FileCodeHash src > minhash.txt

### Output Format

It is a list of tab-separated values (TSV) comprising nine columns.

 - File path
 - SHA-1 file hash
 - Language name (detected by a file extension)
 - Code-hash: content hash excluding whitespace and comments.
 - 1-bit minhash vector:  2048-bit vector of 1-bit min-hash for trigrams of the file.
 - 1-bit minhash vector with identifier normalization:  2048-bit vector of 1-bit min-hash for trigrams ignoring identifier names.
 - File length (byte count)
 - The number of tokens in the file 
 - The number of n-grams in the file

## Estimate file similarity using 1-bit minhash vectors

Another main class `jp.naist.se.codehash.comparison.ComparisonMain` compares 1-bit minhash vectors to estimate source file similarity.
The `minhash.txt` is a file created by `FileCodeHash`.

>        java -classpath CodeHash.jar jp.naist.se.codehash.comparison.ComparisonMain minhash.txt

The hamming distance between a pair of min-hash vectors approximate the similarity of the file pair: `Estimated-similarity = 1 - (The-cardinality-of-XOR-of-two-vectors / 1024)`.   
The estimated value may have some error; a pair of file having actual similarity 0.7 may have an estimated value 0.8.  

The result is a list of tab-separated values comprising the following columns.
Each line shows a pair of files to be compared.

 - CodeHash1: representing a file.  To ignore duplicated files in the list, the tool uses code hash values.
 - CodeHash2: representing the other file of the file pair.
 - TokenLength1: The number of tokens in the file represented by CodeHash1.
 - TokenLength2: The number of tokens in the file represented by CodeHash2.
 - EstimatedSim: Jaccard index estimated by 1-bit minhash vectors of the files.
 - EstimatedSimWithNormalization: Jaccard index estimated by 1-bit minahsh vectors with identifier normalization.
 - FileNames1: File paths whose code-hash equals to CodeHash1.
 - FileNames2: File paths whose code-hash equals to CodeHash2.

The minimum threshold is 0.7.


## Directly compare source files 

The main class `jp.naist.se.codehash.comparison.DirectComparisonMain` directly compares a set of files.
This class calculates the exact similarity between files, while this comparison also can avoid unnecessary comparison using 1-bit minhash vectors.

It takes as command line arguments source file names (or directory names) on your disk.  For example:
>        java -classpath target/CodeHash.jar jp.naist.se.codehash.comparison.DirectComparisonMain 001.c 002.c




### Options

The tool accepts the following options.  Other arguments are regarded as source file (or directory) names.


#### File selection options

- `-lang:[LANGUAGE]`: This option specifies a language name.  Supported languages are CPP, JAVA, ECMASCRIPT, PYTHON, PHP, CSHARP, and PLAINTEXT.  For example, `-lang:PLAINTEXT` compares files in any language as plain text.
- `-group[GROUP-NAME]:[PATH]` specifies a group name for a file or a directory.  This option is meaningful when `-compare:crossgroup` is specified.


#### Comparison Options
- `-n:[NGRAM]` specifies N for N-gram.  The default is trigrams (i.e., `-n:3`).
- `-th:[THRESHOLD]` specifies a similarity threshold (0-1.0).  If all of similarity values for a file pair are less than this threshold, the file pair is excluded from the output.
- `-thnj:[THRESHOLD]` specifies a similarity threshold for normalized jaccard distance.  If the similarity value for a file pair is less than this threshold, the file pair is excluded from the output.
- `-thenj:[THRESHOLD]` specifies a threshold for estimated normalized jaccard distance.  If a similarity estimated by b-bit minhash is less than this threshold, an actual comparison is skipped.
- `-filter:[EXPRESSION]` specifies a condition to report a file pair.  Only file pairs satisfying the condition are included in the output.  If the option is specified multiple times, a file pair must satisfy all of them.  See "Filter expressions" below.
- `-metrics:[NAME,NAME,...]` selects similarity metrics written to the output.  All the metrics are written by default.  A filter expression can use metrics that are not selected by this option.
- `-compare:crossgroup` compares only file pairs across groups.  It skips comparison between file pairs within a group.

#### Filter expressions

A filter expression compares similarity metrics with numbers (or other metrics) and combines the comparisons with logical operators.

- Comparison operators: `>=`, `>`, `<=`, `<`, `==` (or `=`), `!=`
- Logical operators: `&&` (or `and`), `||` (or `or`), `!` (or `not`), and parentheses.  `and` has a higher precedence than `or`.
- Operands: metric names listed in "Similarity metrics" and numbers.  A comparison with `NaN` (e.g. similarity between empty files) is false except for `!=`.

Examples (quote the argument so that a shell does not interpret `>`, `<`, `&`, and `|`):

>        java -classpath target/CodeHash.jar jp.naist.se.codehash.comparison.DirectComparisonMain "-filter:jaccard>=0.7" src
>        java -classpath target/CodeHash.jar jp.naist.se.codehash.comparison.DirectComparisonMain "-filter:jaccard>=0.7 or (overlap-coefficient>=0.9 and exact-jaccard<0.5)" src

`-thnj:0.7` is equivalent to `-filter:jaccard>=0.7`.

### Similarity metrics

 - Jaccard index: Intersection(S1, S2) / Union(S1, S2).
   - `exact-jaccard` compares identifiers as is (i.e., without normalization).
   - `jaccard` compares identifiers with normalization. The similarity metric with `-n:3` has been used in https://doi.org/10.1109/MSR.2017.19.
 - Overlap coefficient: Intersection(S1, S2) / Min(S1, S2).  
   - This is defined in https://aircconline.com/mlaij/V3N1/3116mlaij03.pdf
   - `exact-overlap-coefficient` compares identifiers as is.
   - `overlap-coefficient` compares identifiers with normalization.
 - Overlap similarity: Intersection(S1, S2) / Max(S1, S2).  
   - `exact-overlap-similarity` compares identifiers as is. This similarity metric with `-n:1` has been used by SourcererCC (http://dx.doi.org/10.1145/2884781.2884877).
   - `overlap-similarity` compares identifiers with normalization.

#### Adding a new similarity metric

Each metric is a strategy implementing `jp.naist.se.codehash.similarity.SimilarityMetric`.
To add a metric, implement `getName()` and `calculate(FilePair)`, and register an instance in `SimilarityMetrics` (e.g. in its static initializer).
The registered metric becomes available in `-metrics:` and `-filter:` options automatically.
`FilePair` caches the size of intersection of N-gram multisets so that multiple metrics can share it.

### Output


The program produces a JSON format like this.
The example is the output for two sample files in `src/test/resources/c-sample` (an arithmetic progression and a geometric progression).

>        java -classpath target/CodeHash.jar jp.naist.se.codehash.comparison.DirectComparisonMain src/test/resources/c-sample/arithmetic.c src/test/resources/c-sample/geometric.c

>        {
>          "Files" : [ {
>            "group" : "<default>",
>            "index" : 0,
>            "path" : "/path/to/CodeHash/src/test/resources/c-sample/arithmetic.c",
>            "lang" : "CPP",
>            "byte-sha1" : "6082b553b651ed6fadc0033364fe876b759c0244",
>            "token-sha1" : "0aafcdf2028bd699da81347f47e123586398f2ff",
>            "byte-length" : 319,
>            "token-length" : 79,
>            "ngram-count" : 81
>          }, {
>            "group" : "<default>",
>            "index" : 1,
>            "path" : "/path/to/CodeHash/src/test/resources/c-sample/geometric.c",
>            "lang" : "CPP",
>            "byte-sha1" : "b330cb3ecd212cf933204b8ffa0bd984c5a64614",
>            "token-sha1" : "091097cca238c53c3895334c085804d98285c8ea",
>            "byte-length" : 325,
>            "token-length" : 79,
>            "ngram-count" : 81
>          } ],
>          "Pairs" : [ {
>            "index1" : 0,
>            "index2" : 1,
>            "exact-jaccard" : 0.5283018867924528,
>            "exact-overlap-coefficient" : 0.691358024691358,
>            "exact-overlap-similarity" : 0.691358024691358,
>            "jaccard" : 0.6875,
>            "overlap-coefficient" : 0.8148148148148148,
>            "overlap-similarity" : 0.8148148148148148
>          } ]
>        }

The JSON format is an object comprising a list of files (`Files`) and a list of pair-wise similarity values (`Pairs`). 

### File Attributes

|Attribute   |Value                                                |
|:-----------|:----------------------------------------------------|
|group       |A group name of the file specified by `-group` option|
|index       |Index to identify a file in the list.                |
|path        |A full path of the file.                             |
|lang        |Language name used to recognize tokens in the file   |
|byte-sha1   |SHA1 hash of the file content                        |
|token-sha1  |SHA1 hash of tokens ignoring comments and white space|
|byte-length |The number of bytes in the file                      |
|token-length|The number of tokens in the file                     |
|ngram-count |The number of ngram elements in the file             |

### Pair Attributes

|Attribute        |Value                                                                                     |
|:----------------|:-----------------------------------------------------------------------------------------|
|index1           |The first file to be compared in the list                                                 |
|index2           |The second file to be compared in the list                                                |
|(metric name)    |A similarity value of the file pair for each metric selected by `-metrics` option (all metrics by default)|

The metric names (e.g. `exact-jaccard`, `jaccard`) are listed in "Similarity metrics" above.
The metrics are written in the order of the list.
The identifier normalization used by the metrics without the `exact-` prefix is implemented for C/C++, Java, C Sharp, JavaScript, and Python.



## Analyzing a Git repository

The main class `GitCodeHash` takes as input a CSV file.
Each line must include four items: 
 - Git repository path
 - A lit of blobs with file names
 - An output file path
 - Type of computed hash: codehash, minhash, sha1minhash.  
   - The minhash mode uses MurmurHash3 to compute hash values for trigrams of tokens.
   - The sha1minhash mode uses SHA-1 hash, so it takes significantly longer time compared with minhash.

Example:
>        path/to/first/.git,filelist1.txt,output-codehash.txt,codehash
>        path/to/second/.git,filelist2.txt,output-minhash.txt,minhash

Given this example file, the tool computes codehash for files listed in `filelist1.txt` in the first git repostiory and stores the result to `output-codehash.txt`, and also computes minhash for files listed in `filelist2.txt` in the second git repository and stores the result to `output-minhash.txt`.  

Each file list is a tsv file.  Each line must include three items:
 - Blob ID
 - File name (if available)
 - Programming language type: C, JAVA, JAVASCRIPT, PHP, PYTHON, CSHARP, PLAINTEXT. 
   - For Ruby language, a separated program is available in the repository. 
   - This column can be omitted.  If a line has only two items (Blob ID and file name), the language is detected by the file extension.  Blobs in unsupported languages are skipped.
 
Example for this repository:
>        c01a38dd1b6f66f59515032fd22c9f5b9e46ffce	src/main/java/jp/naist/se/codehash/AntlrTokenReader.java	JAVA
>        f63f44fee52e232adbfc8afbcc323778c4a911ee	src/main/java/jp/naist/se/codehash/CodeHash.java	JAVA
>        5cf76dd798f47ca7e296fa0582888ff1787f97ce	src/main/java/jp/naist/se/codehash/FileType.java	JAVA
>        8005e63674d37076fdd36e70291c8573324dd3ff	src/main/java/jp/naist/se/codehash/GitCodeHash.java	JAVA
>        430582dd4efc0de59502947e5bbd0063cbd73ea1	src/main/java/jp/naist/se/codehash/GitCodeHash.java	JAVA

### Creating a file list using GitFileList

The main class `GitFileList` creates a file list in the two-column format (Blob ID and file name) for a git repository.
It lists all non-binary blobs reachable from `HEAD`, i.e., file contents in the entire history of the current branch.
Each blob is reported only once with one of its file paths.
The tool takes a repository path as an argument (the current directory by default) and writes the list to STDOUT.

>        java -classpath target/CodeHash.jar jp.naist.se.codehash.GitFileList path/to/first > filelist1.txt

The resultant file can be used as a file list for `GitCodeHash`.

### Analyzing a single repository without a file list

If the first argument of `GitCodeHash` is a directory, the tool regards it as a git repository (a working directory or a `.git` directory) and processes the repository without a file list.
It processes the same blobs as `GitFileList` (all blobs reachable from `HEAD`, each blob only once) and detects their languages by file extensions.
The second argument optionally specifies a type of computed hash (`codehash` by default).
The result is written to STDOUT in the same format as the CSV mode.

>        java -classpath target/CodeHash.jar jp.naist.se.codehash.GitCodeHash path/to/first minhash > output-minhash.txt




## Tokenizer usage 

If you would like to see how a file is translated into tokens, you can use `FileTokenizerMain`.
 
>        java -classpath target/CodeHash.jar jp.naist.se.codehash.comparison.FileTokenizerMain 001.c

It generates a list of tokens in a JSON format.
The program reads source code from STDIN when `-` is specified as a file name. 

