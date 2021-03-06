index(project, cmd) ::= <<
[Index](index.md)

### A library for generating Finite State Transducers based on Levenshtein Automata.

Levenshtein transducers accept a query term and return all terms in a
dictionary that are within n spelling errors away from it. They constitute a
highly-efficient (space _and_ time) class of spelling correctors that work very
well when you do not require context while making suggestions.  Forget about
performing a linear scan over your dictionary to find all terms that are
sufficiently-close to the user's query, using a quadratic implementation of the
[Levenshtein distance][wikipedia-levenshtein-distance] or
[Damerau-Levenshtein distance][wikipedia-damerau-levenshtein-distance], these
babies find _all_ the terms from your dictionary in linear time _on the length
of the query term_ (not on the size of the dictionary, on the length of the
query term).

If you need context, then take the candidates generated by the transducer as a
starting place, and plug them into whatever model you're using for context (such
as by selecting the sequence of terms that have the greatest probability of
appearing together).

For a quick demonstration, please visit the [Github Page, here][live-demo].
There's also a command-line interface, [liblevenshtein-java-cli][java-cli].
Please see its [README.md][java-cli-readme] for acquisition and usage information.

[Installation](installation.md)
----------------------------

Below, you will find instructions for how to clone
[$project.github.org$/$project.github.repo$][github-repo] and checkout its
submodules, which include the source code for all supported languages:

$/installation/all(project, cmd, "###")$

Please see [Installation](installation.md) for more details.

[Building](building.md)
--------------------

$/wiki/shell(project, cmd, ["gradleJar"])$

Please see [Building](building.md) for more details.

[Testing](testing.md)
------------------

$/wiki/shell(project, cmd, ["gradleCheck"])$

Please see [Testing](testing.md) for more details.

[Usage](usage.md)
--------------

$/wiki/sample(project, cmd)$

Please see [Usage](usage.md) for more details.

History
------------------

Based largely on the works of [Stoyan Mihov][stoyan-mihov],
[Klaus Schulz][klaus-schulz], and Petar Nikolaev Mitankin, this
library generates Levenshtein transducers using nothing more than an input list
of dictionary terms. The referenced literature includes:
"[Fast String Correction with Levenshtein-Automata][fast-string-correction-2002]",
which defines the algorithm used to generate the Levenshtein automata,
"[Universal Levenshtein Automata. Building and Properties][universal-automata-2005]",
which provided many mathematical proofs that helped me understand the algorithm
and supplied the recursive definitions upon which I based my distance functions,
and
"[Incremental Construction of Minimal Acyclic Finite-State Automata][incremental-construction-dawg-2000]",
that defined an algorithm for constructing Minimal Acyclic Finite-State
Automata in linear time (i.e. MA-FSA, also known as DAWGs: Directed Acyclic Word
Graphs) which I use to store the dictionary of terms.

Upon construction, the list of dictionary terms is indexed as an MA-FSA and a
transducer is initialized according to the maximum edit distance and algorithm
provided. When queried against, the states of the Levenshtein automaton
corresponding to the query term, maximum edit distance, and algorithm specified
are constructed on-demand (lazily) as the automaton is evaluated, as described
by the paper,
"[Fast String Correction with Levenshtein-Automata][fast-string-correction-2002]".
The Levenshtein automaton is intersected with the dictionary MA-FSA, and every
string accepted by both automata is emitted in a list of correction candidates
for the query term.

In contrast to many other Levenshtein automata-based algorithms, the entire
Levenshtein automaton needn't be constructed prior to evaluation, and only those
states of the automaton which are actually required are derived, as needed,
thereby greatly improving the efficiency of the transducer in terms of both
space and time complexity.

The infamous blog post,
"[Damn Cool Algorithms: Levenshtein Automata][damn-cool-algos-levenshtein-automata-2010]",
provided me with a good starting point for this transducer, but the algorithm
proposed therein was too inefficient for my needs.  Yet, it did reference the
paper
"[Fast String Correction with Levenshtein-Automata][fast-string-correction-2002]",
which I ultimately used as the basis of the Levenshtein automata.  The same
paper also serves as the basis of the Levenshtein automata used by the Apache
projects, Lucene and Solr ([Lucene's FuzzyQuery is 100 times faster in 4.0][lucene-fuzzy-2011]),
which itself is based on the project by Jean-Philippe Barrette-LaPierre, [Moman][moman].

Steve Hanov pointed me to the paper,
"[Incremental Construction of Minimal Acyclic Finite-State Automata][incremental-construction-dawg-2000]",
in his blog post entitled,
"[Compressing dictionaries with a DAWG][dict-compress-dawg-2011]".  Another of
Steve's blogs also made an impact on me, namely
"[Fast and Easy Levenshtein distance using a Trie][fast-easy-correct-trie-2011]".

I've become heavily involved with the online movement regarding MOOCs (Massive
Open Online Classrooms), and the following courses taught me much regarding the
material within this library:

1. [Automata][coursera-automata]
2. [Compilers][coursera-compilers]
3. [Natural Language Processing][coursera-nlp]

Finally, I must credit the course which first introduced me to the field of
Information Retrieval, "Mathematical Applications in Search Engine Design",
taught by [Dr. Rao Li][rao-li] at the
[University of South Carolina Aiken][usca]. I highly recommend
that course if you are in the area.

$footer(project, cmd)$
>>
/* vim: set ft=ghmarkdown: */
