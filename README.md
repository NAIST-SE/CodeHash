# CodeHash Tool

This tool computes source file hash for comparing contents ignoring whitespace and comments. 

Build the project using maven.
>        mvn package

The tool takes as input a CSV file.
Each line must include four items: 
 - Git repository path
 - A lit of blobs with file names
 - An output file path
 - Type of computed hash: either codehash or minhash.  
   - You should try codehash first, since minhash mode takes significantly longer time compared with codehash.

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

## Output Format

An output file is a tsv file comprising five columns.
 - Blob ID
 - Programming language type
 - Hash for the file.
   - Code hash: content hash excluding whitespace and comments.  Each token is concatenated by a NUL character.
   - Min-hash: 2048-bit vector of 1-bit min-hash for trigrams of the file.  The hamming distance between a pair of min-hash values can be used to approximate the similarity of the file pair: `Estimated-similarity = (The-number-of-same-bit / 1024) - 1`.   The estimated value may have some error; a pair of file having actual similarity 0.7 may have an estimated value 0.8.  The details are described in a technical paper: https://doi.org/10.1109/MSR.2017.19
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

>        c01a38dd1b6f66f59515032fd22c9f5b9e46ffce	JAVA	ade5fd5466a00690c5607d11cb1ad2b8a7f30400c4f0a457c3208012f8a93cfd1964e806d3a9d73e0f81d72d7c27b582afbda4f3ef2defc9f3780bea72dc89bb7cb3cc7ff68f37afae2d87e1dbdea72e3a7ac3830671fe73f10558848c7a9644438bda4ae66b5b4f65a8a2eaa4712acf328067fea939f12c692ae1767d793e0ec6b3016c3b88481aac6d83050629ff5d89f874c67c636755481aa58e4214a9b6ea15eb63bba7321937313a8b4c26cba08e52412c9cd9a048ae8a23214db87c279ca88acbb5a7b115ad32dd738b7b8fea70f4c03126a17f0f6150238617029a4ab67ca4eb45c98e6bf64422fe671ee8efccc83c3b17f776370d5583d4b5f34c93	907	193
>        f63f44fee52e232adbfc8afbcc323778c4a911ee	JAVA	bef67659b0cb5f6a856f3298401a338183a4a8b806780cae2544316503ac147a3d732c56dd4687c98c0290609c98b4bacb49cf78c72730e328aaadd2e4cc082cf537790de8d50e4f0085d03043f0d398297bd18d1571cf9ff2a34f31cc1a1694f5c18ad69e8b5372211db4682c666bc55df71aa44a75a14cc11c667136ec5b524ce84e26399aa59b06eadb6186a9b5c84c46665b54e344fa4860871ac0186b0b889faa7ea3cfaff01968fb9c439f89ba3eb91628c73bf4493aa698291031ce6753b6fe4fab302b0869a9dec1fddd03f56277d071ab00ff2de36175b0fc225875b52836e00e042e125e42a03fc69f31ec840a591b872ec2f8b8052a53b405c58a	973	183
>        5cf76dd798f47ca7e296fa0582888ff1787f97ce	JAVA	7443eb7867a44971f964e426ed493f1995b014f0a18ccc498200c05049beae9239779d9c3391988dfda366a96e2312499ecda2d90f652a7c58e6f2d65e72f6ade457f4085af2320160ce96a0b46c9ff07cca9486bb82e713ff134834061e3f4c6761764a1b63f4a32db9f5a02e12faef739cd97981bc1d5043b26db6f93ceb8e88c35b454bd13ada25e7311022b115afb2be8fb553df405758249d9b820f600872314ebb0b9e38428c893c714f3666c8f0456005f5911e4f17c425265c6aed955a7c0ac50af21f69392103236e32e3581f1cfc419b1f3ce12d79853610d1123ef225522116678bafd8507720a529d9d845d7f53203717dff0d0b4e1043130f55	4193	656
>        8005e63674d37076fdd36e70291c8573324dd3ff	JAVA	11683af5605109c071d2c7056a9980937598bf7b68a9242eb300e8d703d8249a9de766f9f2d3f46d992c0e2f32ef97c34a0c7554c3a57a5c30b4b580eda26aaee9920204eb7b6ad1ab0fc6ce4e34c194ea7c978f15766c2be8a04e144476039bd662501cdb67500fe89b24770f67fe8feb45437c574718d3ea96f9766f0833c3c8ea333dc37ac4021ef5d278465d672553e41d5659eb81b66c54ed25416963deb80006c6537dfae5b55b591f4055a15ad01968106cdeeb09e4aa9747017abc476be3ded12130e1fe68195bb1581c165d0e2412b19350b0b439193f2e7d215039b2ed642a2465dd68de3ca0753fc438a306fa612f1391d2ad9e9b82b9b05fa652	9545	1844
>        430582dd4efc0de59502947e5bbd0063cbd73ea1	JAVA	1b983af7e0510de175d2c2256e1880b37588aa7b48a9046ea300f0f303d8009a9daf67f03293f46d9b748e2e128f9febcb08771043a5f85430b49386efea6aceed939647eb7b6bc0ab0dc6ce4e3ec3b8a868d38f17342e2bc8a04e944477021bd462401edb72720fe0bb647c0747fe8de96543f45f67389bba84f9726f0abb07caba7b38437ac4021ff4d27d0255272d53e40f5658eba1357c554527417b639f288002d6537ffae5351b791f4015815ad9184a186c5e6b09e4ab93034972b8c77be3edd13130a1fe680b4351480c465d46241ab58370b4b47119bfbf78236019b2c9a46a6465fd78de3ca0753bc458a326b2692f1381d6ad1e9bc6b8b14f8452	5586	1075


