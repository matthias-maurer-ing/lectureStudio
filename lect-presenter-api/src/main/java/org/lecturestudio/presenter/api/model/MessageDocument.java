/*
 * Copyright (C) 2022 TU Darmstadt, Department of Computer Science,
 * Embedded Systems and Applications Group.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.lecturestudio.presenter.api.model;

import com.linkedin.urls.Url;
import com.linkedin.urls.detection.UrlDetector;
import com.linkedin.urls.detection.UrlDetectorOptions;

import java.io.IOException;
import java.util.List;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;

import org.lecturestudio.core.app.dictionary.Dictionary;
import org.lecturestudio.core.model.DocumentType;
import org.lecturestudio.core.pdf.PdfDocument;

/**
 * PDF-based text-message document that contains the text received from remote
 * stream participants.
 *
 * @author Alex Andres
 */
public class MessageDocument extends HtmlToPdfDocument {

	public MessageDocument(Dictionary dict, String message) throws IOException {
		init(createDocument(message));
		setDocumentType(DocumentType.MESSAGE);
		setTitle(dict.get("slides.message"));
	}

	private static PdfDocument createDocument(String message)
			throws IOException {
		PDDocument doc = new PDDocument();

		// Create the first page with the message on it.
		createMessagePage(doc, message);

		return createPdfDocument(doc);
	}

	private static void createMessagePage(PDDocument doc, String message)
			throws IOException {
		var jdoc = Jsoup.parseBodyFragment("");
		jdoc.body().attr("style", "font-family: Helvetica, Sans-Serif; margin-top: 70px; text-align: center;");

		String[] parts = message.split("\n");

		for (String part : parts) {
			if (part.equals("\n")) {
				continue;
			}

			// Search for URLs in the text.
			UrlDetector parser = new UrlDetector(part, UrlDetectorOptions.Default);
			List<Url> found = parser.detect();

			// Each line is encapsulated in a <div>.
			Element div = jdoc.body().appendElement("div");

			if (found.isEmpty()) {
				div.text(part);
			}
			else {
				for (Url url : found) {
					String orig = url.getOriginalUrl();
					int origIndex = part.indexOf(orig);
					String s = part.substring(0, origIndex);
					part = part.substring(origIndex + orig.length());

					// Raw text belongs into a <span> element.
					div.appendElement("span").text(s);

					// Create the link.
					Element a = div.appendElement("a");
					a.attr("href", orig);
					a.attr("target", "_blank");
					a.text(orig);
				}

				// Add remaining raw text.
				if (!part.isEmpty() || !part.isBlank()) {
					div.appendElement("span").text(part);
				}
			}
		}

		renderHtmlPage(jdoc, doc);
	}
}