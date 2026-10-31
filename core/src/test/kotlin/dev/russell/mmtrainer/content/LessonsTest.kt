package dev.russell.mmtrainer.content

import dev.russell.mmtrainer.Claims
import org.junit.Assert.assertTrue
import org.junit.Test

/** Rules every lesson follows: the renderer's tags, worked examples, and arithmetic that holds up. */
class LessonsTest {
    private val topics: List<Topic> = Content.SKILLS.values + Content.DECKS.values
    private val allowedTags = setOf("p", "h3", "ul", "li", "ex", "tip", "table", "tr", "td", "th", "br", "b", "i", "sup", "sub")

    @Test
    fun lessonsUseOnlyTagsTheRendererKnows() {
        for (t in topics) {
            val tags = Regex("</?([a-zA-Z0-9]+)[^>]*>").findAll(t.lesson.body).map { it.groupValues[1] }.toSet()
            assertTrue("${t.id}: ${tags - allowedTags}", (tags - allowedTags).isEmpty())
        }
    }

    @Test
    fun everyLessonHasAWorkedExample() {
        for (t in topics) assertTrue(t.id, "<ex>" in t.lesson.body)
    }

    @Test
    fun arithmeticInLessonsIsRight() {
        val bad = topics.mapNotNull { t ->
            runCatching { Claims.checkClaims(t.lesson.body) }.exceptionOrNull()?.let { "${t.id}: ${it.message}" }
        }
        assertTrue(bad.joinToString("\n"), bad.isEmpty())
    }

    @Test
    fun thousandsAreSeparatedByThinSpaces() {
        val ascii = Regex("""(?<![\d.])\d{1,3}(?: \d{3})+(?!\d)""")
        val bad = topics.flatMap { t -> ascii.findAll(Claims.plain(t.lesson.body)).map { "${t.id}: ${it.value}" } }
        assertTrue(bad.joinToString("\n"), bad.isEmpty())
    }

    @Test
    fun paragraphsStayShort() {
        val para = Regex("<p>(.*?)</p>", RegexOption.DOT_MATCHES_ALL)
        for (t in topics) for (m in para.findAll(t.lesson.body)) {
            val words = Claims.plain(m.groupValues[1]).trim().split(Regex("(?U)\\s+")).size
            assertTrue("${t.id}: $words words in: ${m.groupValues[1]}", words <= 45)
        }
    }
}
