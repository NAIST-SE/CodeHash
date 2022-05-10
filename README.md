# CodeHash Tool

This tool computes source file similarity using 3-gram multiset ignoring whitespace and comments.
The tool can estimate source code siimlarity using b-bit minhash; the tool can skip unnecessary comparison if two files are unlikely similar.

The implementation is a revised version that has been used in our technical paper: 
>        Takashi Ishio, Yusuke Sakaguchi, Kaoru Ito and Katsuro Inoue: 
>        Source File Set Search for Clone-and-Own Reuse Analysis, In Proc. of MSR 2017.
>        <https://doi.org/10.1109/MSR.2017.19>


The source code is written in Java.  You can build the project using Maven.
>        mvn package


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

It takes as command line arguments source file names on your disk.  For example:
>        java -classpath target/CodeHash.jar jp.naist.se.codehash.comparison.DirectComparisonMain 001.c 002.c




### Options

The tool accepts the following options.

#### File selection options

- `-lang:[LANGUAGE]`: This option specifies a language name.  Supported languages are CPP, JAVA, ECMASCRIPT, PYTHON, PHP, and CSHARP.
- `-dir:[DIRNAME]` specifies a directory including files to be compared by the tool.  - `-prefix:[PREFIX]` specifies a prefix filter for file names to be compared by the tool.  This option extracts a subset of files listed by arguments and `-dir` options.

#### Comparison Options
- `-n:[NGRAM]` specifies N for N-gram.  The default is trigrams (i.e., `-n:3`).
- `-th:[THRESHOLD]` specifies a similarity threshold (0-1.0).  If all of similarity values for a file pair are less than this threshold, the file pair is excluded from the output.
- `-thnj:[THRESHOLD]` specifies a similarity threshold for normalized jaccard distance.  If the similarity value for a file pair is less than this threshold, the file pair is excluded from the output.
- `-thenj:[THRESHOLD]` specifies a threshold for estimated normalized jaccard distance.  If a similarity estimated by b-bit minhash is less than this threshold, an actual comparison is skipped.


### Output

The program produces a JSON format like this.

>        {
>          "Files":[ 
>            {index":0,"path":"001.c","lang":"CPP","byte-length":2071,"token-length":662,"ngram-count":664},
>            {index":1,"path":"002.c","lang":"CPP","byte-length":947,"token-length":270,"ngram-count":272}
>          ],
>          "Pairs": [
>            {
>              "index1":0,
>              "index2":1,
>              "jaccard":0.18032786885245902,
>              "estimated-jaccard":0.1826171875,
>              "inclusion1":0.21536144578313254,
>              "inclusion2":0.5257352941176471,
>              "normalization-jaccard":0.32954545454545453,
>              "normalization-estimated-jaccard":0.3203125,
>              "normalization-inclusion1":0.3493975903614458,
>              "normalization-inclusion2":0.8529411764705882
>            }
>          ]
>        }

The JSON format is an object comprising a list of files (`Files`) and a list of pair-wise similarity values (`Pairs`). 

### File Attributes

|Attribute   |Value                                                |
|:-----------|:----------------------------------------------------|
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
|jaccard          |Jaccard index of the ngram multisets of the files (&#x7C;Intersection(F1, F2)&#x7C;/&#x7C;Union(F1, F2)&#x7C;)|
|estimated-jaccard|Estimated jaccard index of the files using minhash                                        |
|inclusion1       |The ratio of common contents in the first file (&#x7C;Intersection(F1, F2)&#x7C;/&#x7C;F1&#x7C;)              |  
|inclusion2       |The ratio of common contents in the second file (&#x7C;Intersection(F1, F2)&#x7C;/&#x7C;F2&#x7C;)             |  
|normalization-*  |The similarity metrics calculated for normalized source code ignoring identifier names    |

The normalization is implemented for C/C++, Java, C Sharp, JavaScript, and Python.



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
 - Programming language type: C, JAVA, JAVASCRIPT, PHP, PYTHON. 
   - For Ruby language, a separated program is available in the repository. 
 
Example for this repository:
>        c01a38dd1b6f66f59515032fd22c9f5b9e46ffce	src/main/java/jp/naist/se/codehash/AntlrTokenReader.java	JAVA
>        f63f44fee52e232adbfc8afbcc323778c4a911ee	src/main/java/jp/naist/se/codehash/CodeHash.java	JAVA
>        5cf76dd798f47ca7e296fa0582888ff1787f97ce	src/main/java/jp/naist/se/codehash/FileType.java	JAVA
>        8005e63674d37076fdd36e70291c8573324dd3ff	src/main/java/jp/naist/se/codehash/GitCodeHash.java	JAVA
>        430582dd4efc0de59502947e5bbd0063cbd73ea1	src/main/java/jp/naist/se/codehash/GitCodeHash.java	JAVA




## Tokenizer usage 

If you would like to see how a file is translated into tokens, you can use `FileTokenizerMain`.
 
>        java -classpath target/CodeHash.jar jp.naist.se.codehash.comparison.FileTokenizerMain 001.c

It generates a list of tokens in a JSON format.
The program reads source code from STDIN when `-` is specified as a file name. 

