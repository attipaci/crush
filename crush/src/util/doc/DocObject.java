/*******************************************************************************
 * Copyright (c) 2013 Attila Kovacs <attila_kovacs[AT]post.harvard.edu>.
 * All rights reserved. 
 * 
 * This file is part of crush.
 * 
 *     crush is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 * 
 *     crush is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 * 
 *     You should have received a copy of the GNU General Public License
 *     along with crush.  If not, see <http://www.gnu.org/licenses/>.
 * 
 * Contributors:
 *     Attila Kovacs <attila_kovacs[AT]post.harvard.edu> - initial API and implementation
 ******************************************************************************/
package util.doc;

import java.util.ArrayList;

//Rules:
// 
//	*** page title ***
// Parse TOC
// ### or more: Section heading until next one (<br> ar the end of lines if more than one)
// === previous line is subsection
// --- subsubsection
// label sections.
// paragraphs start at the identation of the last section...
// lines starting with extra space:
//		-- quotes if only single spaces after first entry
//      -- data title/entry if tab or more than 2 spaces after first entry (entry until next par)
// empty line: next non-empty line is new paragraph (close prior paragraph if open)
// '' : white-space + ' starts a quote. ' + white or punctuation ends a quote.
//	   - check if quoted value is README or GLOSSARY, or README section or GLOSSARY
//       entry (make links to them). (before =)
//     - otherwise, <tt></tt> 
// lines starting with >   : command-line quote
// lines starting with *   : lists.
// lines starting with number : lists.
// <>  : words enclosed in brackets is italicized brackets replaced by &lt; and &gt;
//     : otherwise replace with &lt and &gt 
// words starting with - are options (until =), should be linked to GLOSSARY
// CRUSH should be bold-face
// crush, coadd, etc to be linked to man pages.
// skip tables (lines starting with # are comments)
// @Expressions (ignore if unknown...)


// Line : text, identation
//
// - process READMEs and GLOSSARY 
//   - break into lines
//   - identify sections and paragraphs (create a table of section names/labels)
//   - identify command line quotes (> ..., or extra ident with single space after first entry) remove > from front?
//   - identify italicized <> expressions
//   - replace -- with &mdash; < with &gt and > with &lt
//   - identify lists (* or numbered)
//   

// - cross-reference files.
//   - id/entify options (starting - until =, or quoted until =, or anything immediately before =, or titles)

// Entries: (each carries an identation (which can be a range)
//	Section -- title, level, and subparts
//	Paragraph
//  Comment (if it starts with # or |)
//	Quote
//  definition -- has subparts, identation is more than title but <= data
//  list -- has subparts



public class DocObject {

	DocObject parent, activeChild;
	ArrayList<DocObject> children = new ArrayList<DocObject>();
	
	

	// child identation from parent...
	public static DocObject from(DocObject parent, TextBlock block) {
		return null;
	}


}
