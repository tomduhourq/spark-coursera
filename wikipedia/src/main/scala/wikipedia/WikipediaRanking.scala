package wikipedia

import org.apache.spark.SparkConf
import org.apache.spark.SparkContext
import org.apache.spark.SparkContext._
import org.apache.spark.rdd.RDD

case class WikipediaArticle(title: String, text: String)

object WikipediaRanking {

  val langs = List(
    "JavaScript", "Java", "PHP", "Python", "C#", "C++", "Ruby", "CSS",
    "Objective-C", "Perl", "Scala", "Haskell", "MATLAB", "Clojure", "Groovy")

  val conf: SparkConf = new SparkConf().setAppName("wikipedia").setMaster("local")
  val sc: SparkContext = new SparkContext(conf)
  // Hint: use a combination of `sc.textFile`, `WikipediaData.filePath` and `WikipediaData.parse`
  val wikiRdd: RDD[WikipediaArticle] = sc.textFile(WikipediaData.filePath).map(WikipediaData.parse)

  /** Returns the number of articles on which the language `lang` occurs.
   *  Hint1: consider using method `aggregate` on RDD[T].
   *  Hint2: should you count the "Java" language when you see "JavaScript"?
   *  Hint3: the only whitespaces are blanks " "
   *  Hint4: no need to search in the title :)
   */
  def occurrencesOfLang(lang: String, rdd: RDD[WikipediaArticle]): Int =
    rdd.aggregate(0)((acc, article) => if (article.text.split(" ").contains(lang)) acc + 1 else acc, _ + _)

  /* (1) Use `occurrencesOfLang` to compute the ranking of the languages
   *     (`val langs`) by determining the number of Wikipedia articles that
   *     mention each language at least once. Don't forget to sort the
   *     languages by their occurence, in decreasing order!
   *
   *   Note: this operation is long-running. It can potentially run for
   *   several seconds.
   */
  def rankLangs(langs: List[String], rdd: RDD[WikipediaArticle]): List[(String, Int)] =
    langs map (lang => (lang, occurrencesOfLang(lang, rdd))) sortBy { case (_, repetitions) => - repetitions }

  /* Compute an inverted index of the set of articles, mapping each language
   * to the Wikipedia pages in which it occurs.
   */
  def makeIndex(langs: List[String], rdd: RDD[WikipediaArticle]): RDD[(String, Iterable[WikipediaArticle])] =
    rdd
      .flatMap { article =>
        val split = article.text.split(" ")
        langs.foldLeft(List.empty[(String, WikipediaArticle)])((acc, lang) => if (split.contains(lang)) (lang, article) :: acc else acc)
      }
      .groupByKey()

  /* (2) Compute the language ranking again, but now using the inverted index. Can you notice
   *     a performance improvement?
   *
   *   Note: this operation is long-running. It can potentially run for
   *   several seconds.
   */
  def rankLangsUsingIndex(index: RDD[(String, Iterable[WikipediaArticle])]): List[(String, Int)] =
    index
      .map { case (lang, articles) => (lang, articles.size) }
      .sortBy { case (_, repetitions) => - repetitions }
      .collect()
      .toList

  /* (3) Use `reduceByKey` so that the computation of the index and the ranking is combined.
   *     Can you notice an improvement in performance compared to measuring *both* the computation of the index
   *     and the computation of the ranking? If so, can you think of a reason?
   *
   *   Note: this operation is long-running. It can potentially run for
   *   several seconds.
   */
  def rankLangsReduceByKey(langs: List[String], rdd: RDD[WikipediaArticle]): List[(String, Int)] =
    rdd
      .flatMap { article =>
        val split = article.text.split(" ")
        langs.foldLeft(List.empty[(String, Int)])((acc, lang) => if (split.contains(lang)) (lang, 1) :: acc else acc)
      }
      .reduceByKey(_ + _)
      .sortBy { case (_, reps) => - reps }
      .collect()
      .toList


  def main(args: Array[String]) {

    /* Languages ranked according to (1) */
    val langsRanked: List[(String, Int)] = timed("Part 1: naive ranking", rankLangs(langs, wikiRdd))

    /* An inverted index mapping languages to wikipedia pages on which they appear */
    def index: RDD[(String, Iterable[WikipediaArticle])] = makeIndex(langs, wikiRdd)

    /* Languages ranked according to (2), using the inverted index */
    val langsRanked2: List[(String, Int)] = timed("Part 2: ranking using inverted index", rankLangsUsingIndex(index))

    /* Languages ranked according to (3) */
    val langsRanked3: List[(String, Int)] = timed("Part 3: ranking using reduceByKey", rankLangsReduceByKey(langs, wikiRdd))

    /* Output the speed of each ranking */
    println(timing)
    sc.stop()
  }

  val timing = new StringBuffer
  def timed[T](label: String, code: => T): T = {
    val start = System.currentTimeMillis()
    val result = code
    val stop = System.currentTimeMillis()
    timing.append(s"Processing $label took ${stop - start} ms.\n")
    result
  }
}
