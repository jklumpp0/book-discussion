package com.octoberdiscussion.message

import org.commonmark.parser.Parser
import org.commonmark.renderer.html.HtmlRenderer

private val markdownParser: Parser = Parser.builder().build()

// escapeHtml(true) renders raw HTML found in the source as literal escaped text instead of
// passing it through verbatim - this is what neutralizes `<img src=x onerror=...>` payloads.
// sanitizeUrls(true) restricts markdown link/image URL schemes (blocks javascript:) as
// defense in depth on top of that.
private val markdownRenderer: HtmlRenderer =
    HtmlRenderer
        .builder()
        .escapeHtml(true)
        .sanitizeUrls(true)
        .build()

fun renderMessageMarkdown(source: String): String = markdownRenderer.render(markdownParser.parse(source))
