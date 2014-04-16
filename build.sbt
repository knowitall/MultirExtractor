name := "MultirExtractor"

version := "0.1"

scalaVersion := "2.10.1"

fork := true

javaOptions in run += "-Xmx12G"

javaOptions in run += "-Djava.util.Arrays.useLegacyMergeSort=true"

libraryDependencies ++= Seq(
  "org.apache.commons" % "commons-io" % "1.3.2",
  "org.apache.commons" % "commons-lang3" % "3.1",
  "edu.stanford.nlp" % "stanford-corenlp" % "1.3.5",
  "edu.washington.cs.knowitall.stanford-corenlp" % "stanford-ner-models" % "1.3.5",
  "edu.washington.cs.knowitall.stanford-corenlp" % "stanford-postag-models" % "1.3.5",
  "edu.washington.cs.knowitall.stanford-corenlp" % "stanford-dcoref-models" % "1.3.5",
  "edu.washington.cs.knowitall.stanford-corenlp" % "stanford-parse-models" % "1.3.5",
  "edu.washington.cs.knowitall.stanford-corenlp" % "stanford-sutime-models" % "1.3.5",
  "org.apache.derby" % "derby" % "10.10.1.1",
  "org.apache.derby" % "derbyclient" % "10.9.1.0",
  "edu.washington.cs.knowitall" % "reverb-core" % "1.4.3",
  "edu.washington.cs.knowitall.nlptools" % "nlptools-core_2.10" % "2.4.4",
  "edu.washington.cs.knowitall.nlptools" % "nlptools-chunk-opennlp_2.10" % "2.4.4",
  "edu.mit" % "jwi" % "2.2.3",
  "postgresql" % "postgresql" % "9.0-801.jdbc4",
  "edu.washington.cs.knowitall.nlptools" % "nlptools-wordnet-uw_2.10" % "2.4.4",
  "org.apache.hadoop" % "hadoop-core" % "0.20.2")


EclipseKeys.createSrc := EclipseCreateSrc.Default + EclipseCreateSrc.Resource
