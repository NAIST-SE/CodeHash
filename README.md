# CodeHash Tool

This tool computes source file similarity using 3-gram multiset ignoring whitespace and comments.
The implementation is a revised version that has used in our technical paper: 
>        Takashi Ishio, Yusuke Sakaguchi, Kaoru Ito and Katsuro Inoue: 
>        Source File Set Search for Clone-and-Own Reuse Analysis, In Proc. of MSR 2017.
>        <https://doi.org/10.1109/MSR.2017.19>


The source code is written in Java.  You can build the project using Maven.
>        mvn package

## Compare source files 

The main class `jp.naist.se.codehash.comparison.DirectComparisonMain` compares a set of files.

It takes as command line arguments source file names on your disk.  For example:
>        java -classpath target/CodeHash.jar jp.naist.se.codehash.comparison.DirectComparisonMain 001.c 002.c

An option `-lang:[LANGUAGE]` specifies a language name.  Supported languages are CPP, JAVA, ECMASCRIPT, PYTHON, PHP, and CSHARP.

The program produces a JSON format.

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

|Attribute   |Value                                             |
|:-----------|:-------------------------------------------------|
|index       |Index to identify a file in the list.             |
|path        |A full path of the file.                          |
|lang        |Language name used to recognize tokens in the file|
|byte-length |The number of bytes in the file                   |
|token-length|The number of tokens in the file                  |
|ngram-count |The number of ngram elements in the file          |

### Pair Attributes

|Attribute        |Value                                                                                 |
|:----------------|:-------------------------------------------------------------------------------------|
|index1           |The first file to be compared in the list                                             |
|index2           |The second file to be compared in the list                                            |
|jaccard          |Jaccard index of the ngram multisets of the files (|F1 \cap F2|/|F1 \cup F2|)         |
|estimated-jaccard|Estimated jaccard index of the files using minhash                                    |
|inclusion1       |The ratio of common contents in the first file (|F1 \cap F2|/|F1|)                    |  
|inclusion2       |The ratio of common contents in the second file (|F1 \cap F2|/|F2|)                   |  
|normalization-*  |The similarity metrics calculated for normalized source code ignoring identifier names|

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



## Analyzing files

Another main class `FileCodeHash` accepts file names (or directory names) and an option `-minhash`.
The main class reports hash values for each file with file names in addition to SHA-1 hash, code hash, and minhash (optional).  

The following command line is an example to compute minhash values for source files in `src` directory.

>        java -classpath CodeHash.jar jp.naist.se.codehash.FileCodeHash -minhash src

## Output Format

An output file is a tsv file comprising five columns.
 - Blob ID
 - Programming language type
 - Source code hash: content hash excluding whitespace and comments.  Each token is concatenated by a NUL character.
 - Min-hash (optional): 2048-bit vector of 1-bit min-hash for trigrams of the file.  
   - The hamming distance between a pair of min-hash values can be used to approximate the similarity of the file pair: `Estimated-similarity = 1 - (The-cardinality-of-XOR-of-two-vectors / 1024)`.   The estimated value may have some error; a pair of file having actual similarity 0.7 may have an estimated value 0.8.  
 - File size 
 - The number of tokens in the file

The file may miss files whose contents cannot be processed due to grammatical errors or unavailability (e.g. deleted from the repository).

Example of Code Hash Mode Result:

>        c01a38dd1b6f66f59515032fd22c9f5b9e46ffce	JAVA	128da0723e6417906aa5cacc3ebdf562adfa8651	907	193
>        f63f44fee52e232adbfc8afbcc323778c4a911ee	JAVA	e19d03cb0c05be3aa22ce208ae8abfc633d4ee5c	973	183
>        5cf76dd798f47ca7e296fa0582888ff1787f97ce	JAVA	24845e176c757cc000a2b4b410951dab601edba8	4193	656
>        8005e63674d37076fdd36e70291c8573324dd3ff	JAVA	1630ff51700724eccab03b576d7fb9a0e4bc95f7	9545	1844
>        430582dd4efc0de59502947e5bbd0063cbd73ea1	JAVA	0d9e73ba2795a256f315a9511971ca1c8197d937	5586	1075


Example of Min-Hash Mode Result:

>        c01a38dd1b6f66f59515032fd22c9f5b9e46ffce	JAVA	128da0723e6417906aa5cacc3ebdf562adfa8651	cc594b369978eded0574c7ae2869c513ca184024acf1d3e024c32bba507278cc40a56a32aa788e0da8fc5bdfe0fd3fc74be510c19539fbc68d8f9c15d92138c733b60fc23975897843944a9818f46856aa2f44b9c6d56a00a7a5b01a9f5a0f4d11dcd3c6d6466a63c5eb5a454f2a3d72a4a603faad757985ff8660a823c7a12958ed95fbad6c4ceb7616d2c9ffaa117c005e2f36f53020e1d32c707bbd52e89ccd0e78923cc1ccee84cf564faca47767a482387a35749a7396e18261bada28dce4835f1465fbe520b6f74fb0f95bc8f10ac5ead615dadd9cdd39808705ec95926dc0299359def3d453477c4963ea6a152efd568264445f90f744eb816a2c70e7	907	193
>        ff63f44fee52e232adbfc8afbcc323778c4a911ee	JAVA	e19d03cb0c05be3aa22ce208ae8abfc633d4ee5c	30871ab88f7815cc4978932c36c71512bb35c4b1ab740cc7974a2f1f14db076246142be1eb367768e2b1d7f8fac970ca0bcb625bb6fdab328f16d89c941caac0232b77feb95cf888593e61014ebd242c8c66d2a602e0646daaa55610e4029e92b2d10512fe315e71c2c73c402d27f5d6ed0a79feb710a44c6121f0ead7c8180f50f024369708e5fb3a74906f9d5f3fbe638705ea09efb7d18ab92abbeeaf375c911138c63d3ba6a90652fc12a01237c3c8d9415c726a13a97220edacb9c342c504363c95b44a27ad28c2c7531137480ec5a700cee924dbb9ffccd0231b8131e72f0a801458b78e94cc73eea973775bc79535d5c8eed63d10de1c24c9961c3984	973	183
>        5cf76dd798f47ca7e296fa0582888ff1787f97ce	JAVA	24845e176c757cc000a2b4b410951dab601edba8	101685acb452a708d6e3979b44a23d6082bb71a5c2f1c39db894ba31f51ae5507549dc609afaf173c6f9f6f5c56a25f0cfc725dbe4e064070767b516fd84e3d2d01ab5d3473857c2292a3aee3f3a83671f8c139242857020a105c50828d0e2f404a2842226e0ca02d0ca3ffdf7927476750e0eff455676aac31a61e05ad3ccbf63f447403dcdf7f60bb6d41338b300633d0031a2d0b12fe162643404e439007e51f2b04c151949006c4a72eea6b4be77a056dcd864ff6ad5f70813a6bf40074560b4ec7749fdc1ef265e1f7d833f4ac5c5e38ad91b39bdb595f920cf4a7cf45190922cd0df504955a9cb0313168929132c09d4ab235a28b8a155ec0cf02a2fd7	4193	656
>        8005e63674d37076fdd36e70291c8573324dd3ff	JAVA	1630ff51700724eccab03b576d7fb9a0e4bc95f7	3d63eeece0e80dd91d664d20660f0f2bc900712da08bcda7ad633a8a5e5087008e8c3a708c4e2dffa0637c34ce879286ffed94fb93db97a31bd608e6f1e0bb12114233fa9c398c0253bdbb11cfaef79f04b22a9693e4982b3951d707804ab528c6fd234a0f3d5294632a3555c945938ca21d8df28ec9c21a7ff14506b30799a594cec0e5d960c6eaec6560d9e9bfada67c8516bd106bd02423e0c7717477f17c745e5aff1676c7cdb89f9c58f580d6c5663a4b4a35dc4bbc5745ec29f56e2acc05122515b4af9fa394f2d2dd3b2b661892aadfbb540840b98f11c06c3dc0b71f4d4b39b4d0fb34d9097451ba0e6078405673b4007d438852fbc0445d13013506	9545	1844
>        430582dd4efc0de59502947e5bbd0063cbd73ea1	JAVA	0d9e73ba2795a256f315a9511971ca1c8197d937	3de5eee6eef88fd118e6cf28642f072ad900612da10389a7a5633a8a4cd087308e04bb608e4e2deda0636c34c607d2865bcd84fb96cb96e317d34bf6f1e0fb13114037fa9939850253bdab01cdaef49f80a26a9e93e0d82b3951d796a04ab528c7fda3498f3d12bf623b35754946938e22190db2adcbd21a3bf04505a3079b659cc8e4e7d920c6eae80360c9f9bb2ca47eb716ad086ff22a2fc1c7f17c7ff178740f7afd1666e6cd38935c58f4b076c5e63a6b5235d469fcd544ec29dd6eaacc0d123335b1ad1f239136fa153b3b6518c2badf3d7c0871b9ef01486c3bc037170e7b1bb650fb34d90d7553fb8c607c467057f4804c038252bed0441d17196d46	5586	1075



## Tokenizer usage 

If you would like to see how a file is translated into tokens, you can use `FileTokenizerMain`.
 
>        java -classpath target/CodeHash.jar jp.naist.se.codehash.comparison.FileTokenizerMain 001.c

It generates a list of tokens in a JSON format.
The program reads source code from STDIN when `-` is specified as a file name. 

