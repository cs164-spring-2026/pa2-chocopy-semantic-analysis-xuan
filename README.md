# CS 164: Programming Assignment 2

[ChocoPy Specification]: https://sites.google.com/berkeley.edu/cs164-sp26/chocopy?authuser=1

Note: Users running Windows should replace the colon (`:`) with a semicolon (`;`) in the classpath argument for all command listed below.

## Getting started

Run the following command to build your semantic analysis, and then run all the provided tests:

    mvn clean package

    java -cp "chocopy-ref.jar:target/assignment.jar" chocopy.ChocoPy \
        --pass=.s --dir src/test/data/pa2/sample/ --test


In the starter code, only two tests should pass. Your objective is to implement a semantic analysis that passes all the provided tests and meets the assignment specifications.

You can also run the semantic analysis on one input file at at time. In general, running the semantic analysis on a ChocoPy program is a two-step process. First, run the reference parser to get an AST JSON:


    java -cp "chocopy-ref.jar:target/assignment.jar" chocopy.ChocoPy \
        --pass=r <chocopy_input_file> --out <ast_json_file> 


Second, run the semantic analysis on the AST JSON to get a typed AST JSON:

    java -cp "chocopy-ref.jar:target/assignment.jar" chocopy.ChocoPy \
        -pass=.s  <ast_json_file> --out <typed_ast_json_file>


The `src/tests/data/pa2/sample` directory already contains the AST JSONs for the test programs (with extension `.ast`); therefore, you can skip the first step for the sample test programs.

To observe the output of the reference implementation of the semantic analysis, replace the second step with the following command:


    java -cp "chocopy-ref.jar:target/assignment.jar" chocopy.ChocoPy \
        --pass=.r <ast_json_file> --out <typed_ast_json_file>


In either step, you can omit the `--out <output_file>` argument to have the JSON be printed to standard output instead.

You can combine AST generation by the reference parser with your 
semantic analysis as well:

    java -cp "chocopy-ref.jar:target/assignment.jar" chocopy.ChocoPy \
        --pass=rs <chocopy_input_file> --out <typed_ast_json_file>


## Assignment specifications

See the PA2 specification on the course
website for a detailed specification of the assignment.

Refer to the ChocoPy Specification on the CS164 web site
for the specification of the ChocoPy language. 

## Receiving updates to this repository

Add the `upstream` repository remotes (you only need to do this once in your local clone):

    git remote add upstream https://github.com/cs164-spring-2026/pa2-chocopy-semantic-analysis.git


To sync with updates upstream:

    git pull upstream master

## Submission writeup

Team member 1: Jeffrey Fang

I have consumed 29 late hours for this assignment, putting me over the limit by 5.

1. How many passes does your semantic analysis perform over the AST? List the names of these
passes with their class names and briefly explain the purpose of each pass.

There are several passes over the AST by the declarationanalyzer. First, we need the class names, to avoid shadowing them with variables before the classes are defined, which is not possible within one pass. In addition, in order to implement global/nonlocal functionality, we need to check variable definitions from top semantic scope, to the bottom. As a result, each time we enter a semantic scope (each function), there are two passes over the AST. One to preload variable names, function names, and class names, and a second one where we actually dispatch into function and class bodies. As a result, my dispatchanalyzer uses three passes over the AST, though I suspect the first two can be combined, which I did not have time to clean up. The typeanalyzer, on the other hand, only uses one pass through the AST, leading to 4 passes in total.


2. What was the hardest component to implement in this assignment? Why was it challenging?

The most difficult component of the project was the need for forward definitions. For example, a "global x" statement is valid even if x has not yet been defined if we read the program in order, as long as it is defined later. This was something I did not consider when creating my initial analyzers and semantic structure. When the issues came to light during testing, I had to completely redesign my code to do a preprocessing pass. This included changing analyze(VarDef), analyze(FuncDef), and analyze(ClassDef), as they no longer had to insert their information as a new node in my SymbolTable. 
This also became a requirement for nested functions. A second preprocessing step was needed every time we jumped into a function, which required specific cases to be considered when loading our local scopes. It was challenging keeping track of what checks needed to be done in the preprocessing step, which should be reserved for the more general pass, and even if they should be there at all. For example, shadowing checks needed to be done in side nested function preprocessing, but was a mistake to put in global preprocessing.


3. When type checking ill-typed expressions, why is it important to recover by inferring
the most specific type? What is the problem if we simply infer the type object
for every ill-typed expression? Explain your answer with the help of examples in the
student contributed/bad types.py test.

When checking ill-typed expressions, using a general object might cause us to miss other errors that may arise if we fix the first one. Let's look at the simple test in bad types.py. We have an obvious ill-typed expression in y+1, bool+int, that we have to deal with. In my analyzer and the reference, this would be treated as an int as we continue on. As a result, we are able to see that we have no error assigning x to an int, but we do have an error assigning z(bool) to an int. If we had instead used the general object type, this second error would've been missed. 
Similarly, we have an ill-typed expression s[y]. Again, if this was treated as an object, we would have no error assigning a boolean variable to this value. However, if we use the more specific type string, we are able to recognize the issue in the assignStmt outside of the ill-typed expression.



(Students should edit this section with their write-up)
