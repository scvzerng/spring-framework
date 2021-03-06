/*
 * Copyright 2002-2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.web.util.pattern;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.hamcrest.Matchers;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import org.springframework.http.server.reactive.PathContainer;
import org.springframework.http.server.reactive.PathContainer.Element;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.util.pattern.PathPattern.PathMatchResult;
import org.springframework.web.util.pattern.PathPattern.PathRemainingMatchInfo;

import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Exercise matching of {@link PathPattern} objects.
 *
 * @author Andy Clement
 */
public class PathPatternMatcherTests {

	private char separator = PathPatternParser.DEFAULT_SEPARATOR;


	@Test
	public void pathContainer() {
		assertEquals("[/][abc][/][def]",elementsToString(toPathContainer("/abc/def").elements()));
		assertEquals("[abc][/][def]",elementsToString(toPathContainer("abc/def").elements()));
		assertEquals("[abc][/][def][/]",elementsToString(toPathContainer("abc/def/").elements()));
		assertEquals("[abc][/][/][def][/][/]",elementsToString(toPathContainer("abc//def//").elements()));
		assertEquals("[/]",elementsToString(toPathContainer("/").elements()));
		assertEquals("[/][/][/]",elementsToString(toPathContainer("///").elements()));
	}

	@Test
	public void matching_LiteralPathElement() {
		checkMatches("foo", "foo");
		checkNoMatch("foo", "bar");
		checkNoMatch("foo", "/foo");
		checkNoMatch("/foo", "foo");
		checkMatches("/f", "/f");
		checkMatches("/foo", "/foo");
		checkNoMatch("/foo", "/food");
		checkNoMatch("/food", "/foo");
		checkMatches("/foo/", "/foo/");
		checkMatches("/foo/bar/woo", "/foo/bar/woo");
		checkMatches("foo/bar/woo", "foo/bar/woo");
	}

	@Test
	public void basicMatching() {
		checkMatches("", "");
		checkMatches("", null);
		checkNoMatch("/abc", "/");
		checkMatches("/", "/");
		checkNoMatch("/", "/a");
		checkMatches("foo/bar/", "foo/bar/");
		checkNoMatch("foo", "foobar");
		checkMatches("/foo/bar", "/foo/bar");
		checkNoMatch("/foo/bar", "/foo/baz");
	}

	private void assertMatches(PathPattern pp, String path) {
		assertTrue(pp.matches(toPathContainer(path)));
	}

	private void assertNoMatch(PathPattern pp, String path) {
		assertFalse(pp.matches(toPathContainer(path)));
	}
	
	@Test
	public void optionalTrailingSeparators() {
		PathPattern pp;
		// LiteralPathElement
		pp = parse("/resource");
		assertMatches(pp,"/resource");
		assertMatches(pp,"/resource");
		assertMatches(pp,"/resource/");
		assertNoMatch(pp,"/resource//");
		pp = parse("/resource/");
		assertNoMatch(pp,"/resource");
		assertMatches(pp,"/resource/");
		assertNoMatch(pp,"/resource//");

		pp = parse("res?urce");
		assertNoMatch(pp,"resource//");
		// SingleCharWildcardPathElement
		pp = parse("/res?urce");
		assertMatches(pp,"/resource");
		assertMatches(pp,"/resource/");
		assertNoMatch(pp,"/resource//");
		pp = parse("/res?urce/");
		assertNoMatch(pp,"/resource");
		assertMatches(pp,"/resource/");
		assertNoMatch(pp,"/resource//");

		// CaptureVariablePathElement
		pp = parse("/{var}");
		assertMatches(pp,"/resource");
		assertEquals("resource",pp.matchAndExtract(toPathContainer("/resource")).getUriVariables().get("var"));
		assertMatches(pp,"/resource/");
		assertEquals("resource",pp.matchAndExtract(toPathContainer("/resource/")).getUriVariables().get("var"));
		assertNoMatch(pp,"/resource//");
		pp = parse("/{var}/");
		assertNoMatch(pp,"/resource");
		assertMatches(pp,"/resource/");
		assertEquals("resource",pp.matchAndExtract(toPathContainer("/resource/")).getUriVariables().get("var"));
		assertNoMatch(pp,"/resource//");
		
		// CaptureTheRestPathElement
		pp = parse("/{*var}");
		assertMatches(pp,"/resource");
		assertEquals("/resource",pp.matchAndExtract(toPathContainer("/resource")).getUriVariables().get("var"));
		assertMatches(pp,"/resource/");
		assertEquals("/resource/",pp.matchAndExtract(toPathContainer("/resource/")).getUriVariables().get("var"));
		assertMatches(pp,"/resource//");
		assertEquals("/resource//",pp.matchAndExtract(toPathContainer("/resource//")).getUriVariables().get("var"));
		assertMatches(pp,"//resource//");
		assertEquals("//resource//",pp.matchAndExtract(toPathContainer("//resource//")).getUriVariables().get("var"));
		
		// WildcardTheRestPathElement
		pp = parse("/**");
		assertMatches(pp,"/resource");
		assertMatches(pp,"/resource/");
		assertMatches(pp,"/resource//");
		assertMatches(pp,"//resource//");
		
		// WildcardPathElement
		pp = parse("/*");
		assertMatches(pp,"/resource");
		assertMatches(pp,"/resource/");
		assertNoMatch(pp,"/resource//");
		pp = parse("/*/");
		assertNoMatch(pp,"/resource");
		assertMatches(pp,"/resource/");
		assertNoMatch(pp,"/resource//");

		// RegexPathElement
		pp = parse("/{var1}_{var2}");
		assertMatches(pp,"/res1_res2");
		assertEquals("res1",pp.matchAndExtract(toPathContainer("/res1_res2")).getUriVariables().get("var1"));
		assertEquals("res2",pp.matchAndExtract(toPathContainer("/res1_res2")).getUriVariables().get("var2"));
		assertMatches(pp,"/res1_res2/");
		assertEquals("res1",pp.matchAndExtract(toPathContainer("/res1_res2/")).getUriVariables().get("var1"));
		assertEquals("res2",pp.matchAndExtract(toPathContainer("/res1_res2/")).getUriVariables().get("var2"));
		assertNoMatch(pp,"/res1_res2//");
		pp = parse("/{var1}_{var2}/");
		assertNoMatch(pp,"/res1_res2");
		assertMatches(pp,"/res1_res2/");
		assertEquals("res1",pp.matchAndExtract(toPathContainer("/res1_res2/")).getUriVariables().get("var1"));
		assertEquals("res2",pp.matchAndExtract(toPathContainer("/res1_res2/")).getUriVariables().get("var2"));
		assertNoMatch(pp,"/res1_res2//");
		pp = parse("/{var1}*");
		assertMatches(pp,"/a");
		assertMatches(pp,"/a/");
		assertNoMatch(pp,"/"); // no characters for var1
		assertNoMatch(pp,"//"); // no characters for var1

		// Now with trailing matching turned OFF
		PathPatternParser parser = new PathPatternParser();
		parser.setMatchOptionalTrailingSlash(false);
		// LiteralPathElement
		pp = parser.parse("/resource");
		assertMatches(pp,"/resource");
		assertNoMatch(pp,"/resource/");
		assertNoMatch(pp,"/resource//");
		pp = parser.parse("/resource/");
		assertNoMatch(pp,"/resource");
		assertMatches(pp,"/resource/");
		assertNoMatch(pp,"/resource//");

		// SingleCharWildcardPathElement
		pp = parser.parse("/res?urce");
		assertMatches(pp,"/resource");
		assertNoMatch(pp,"/resource/");
		assertNoMatch(pp,"/resource//");
		pp = parser.parse("/res?urce/");
		assertNoMatch(pp,"/resource");
		assertMatches(pp,"/resource/");
		assertNoMatch(pp,"/resource//");

		// CaptureVariablePathElement
		pp = parser.parse("/{var}");
		assertMatches(pp,"/resource");
		assertEquals("resource",pp.matchAndExtract(toPathContainer("/resource")).getUriVariables().get("var"));
		assertNoMatch(pp,"/resource/");
		assertNoMatch(pp,"/resource//");
		pp = parser.parse("/{var}/");
		assertNoMatch(pp,"/resource");
		assertMatches(pp,"/resource/");
		assertEquals("resource",pp.matchAndExtract(toPathContainer("/resource/")).getUriVariables().get("var"));
		assertNoMatch(pp,"/resource//");
				
		// CaptureTheRestPathElement
		pp = parser.parse("/{*var}");
		assertMatches(pp,"/resource");
		assertEquals("/resource",pp.matchAndExtract(toPathContainer("/resource")).getUriVariables().get("var"));
		assertMatches(pp,"/resource/");
		assertEquals("/resource/",pp.matchAndExtract(toPathContainer("/resource/")).getUriVariables().get("var"));
		assertMatches(pp,"/resource//");
		assertEquals("/resource//",pp.matchAndExtract(toPathContainer("/resource//")).getUriVariables().get("var"));
		assertMatches(pp,"//resource//");
		assertEquals("//resource//",pp.matchAndExtract(toPathContainer("//resource//")).getUriVariables().get("var"));
				
		// WildcardTheRestPathElement
		pp = parser.parse("/**");
		assertMatches(pp,"/resource");
		assertMatches(pp,"/resource/");
		assertMatches(pp,"/resource//");
		assertMatches(pp,"//resource//");
				
		// WildcardPathElement
		pp = parser.parse("/*");
		assertMatches(pp,"/resource");
		assertNoMatch(pp,"/resource/");
		assertNoMatch(pp,"/resource//");
		pp = parser.parse("/*/");
		assertNoMatch(pp,"/resource");
		assertMatches(pp,"/resource/");
		assertNoMatch(pp,"/resource//");

		// RegexPathElement
		pp = parser.parse("/{var1}_{var2}");
		assertMatches(pp,"/res1_res2");
		assertEquals("res1",pp.matchAndExtract(toPathContainer("/res1_res2")).getUriVariables().get("var1"));
		assertEquals("res2",pp.matchAndExtract(toPathContainer("/res1_res2")).getUriVariables().get("var2"));
		assertNoMatch(pp,"/res1_res2/");
		assertNoMatch(pp,"/res1_res2//");
		pp = parser.parse("/{var1}_{var2}/");
		assertNoMatch(pp,"/res1_res2");
		assertMatches(pp,"/res1_res2/");
		assertEquals("res1",pp.matchAndExtract(toPathContainer("/res1_res2/")).getUriVariables().get("var1"));
		assertEquals("res2",pp.matchAndExtract(toPathContainer("/res1_res2/")).getUriVariables().get("var2"));
		assertNoMatch(pp,"/res1_res2//");
		pp = parser.parse("/{var1}*");
		assertMatches(pp,"/a");
		assertNoMatch(pp,"/a/");
		assertNoMatch(pp,"/"); // no characters for var1
		assertNoMatch(pp,"//"); // no characters for var1
	}

	@Test
	public void pathRemainderBasicCases_spr15336() {
		// Cover all PathElement kinds
		assertEquals("/bar", getPathRemaining("/foo","/foo/bar").getPathRemaining().value());
		assertEquals("/", getPathRemaining("/foo","/foo/").getPathRemaining().value());
		assertEquals("/bar",getPathRemaining("/foo*","/foo/bar").getPathRemaining().value());
		assertEquals("/bar", getPathRemaining("/*","/foo/bar").getPathRemaining().value());
		assertEquals("/bar", getPathRemaining("/{foo}","/foo/bar").getPathRemaining().value());
		assertNull(getPathRemaining("/foo","/bar/baz"));
		assertEquals("",getPathRemaining("/**","/foo/bar").getPathRemaining().value());
		assertEquals("",getPathRemaining("/{*bar}","/foo/bar").getPathRemaining().value());
		assertEquals("/bar",getPathRemaining("/a?b/d?e","/aab/dde/bar").getPathRemaining().value());
		assertEquals("/bar",getPathRemaining("/{abc}abc","/xyzabc/bar").getPathRemaining().value());
		assertEquals("/bar",getPathRemaining("/*y*","/xyzxyz/bar").getPathRemaining().value());
		assertEquals("",getPathRemaining("/","/").getPathRemaining().value());
		assertEquals("a",getPathRemaining("/","/a").getPathRemaining().value());
		assertEquals("a/",getPathRemaining("/","/a/").getPathRemaining().value());
		assertEquals("/bar",getPathRemaining("/a{abc}","/a/bar").getPathRemaining().value());
		assertEquals("/bar", getPathRemaining("/foo//","/foo///bar").getPathRemaining().value());
	}

	@Test
	public void encodingAndBoundVariablesCapturePathElement() {
		checkCapture("{var}","f%20o","var","f o");
		checkCapture("{var1}/{var2}","f%20o/f%7Co","var1","f o","var2","f|o");
		checkCapture("{var1}/{var2}","f%20o/f%7co","var1","f o","var2","f|o"); // lower case encoding
		checkCapture("{var:foo}","foo","var","foo");
		checkCapture("{var:f o}","f%20o","var","f o"); // constraint is expressed in non encoded form
		checkCapture("{var:f.o}","f%20o","var","f o");
		checkCapture("{var:f\\|o}","f%7co","var","f|o");	
	}
	
	@Test
	public void encodingAndBoundVariablesCaptureTheRestPathElement() {
		checkCapture("/{*var}","/f%20o","var","/f o");
		checkCapture("{var1}/{*var2}","f%20o/f%7Co","var1","f o","var2","/f|o");
		checkCapture("/{*var}","/foo","var","/foo");
		checkCapture("/{*var}","/f%20o","var","/f o");
		checkCapture("/{*var}","/f%20o","var","/f o");
		checkCapture("/{*var}","/f%7co","var","/f|o");
	}

	@Test
	public void encodingAndBoundVariablesRegexPathElement() {
		checkCapture("/{var1:f o}_ _{var2}","/f%20o_%20_f%7co","var1","f o","var2","f|o");
		checkCapture("/{var1}_{var2}","/f%20o_foo","var1","f o","var2","foo");
		checkCapture("/{var1}_ _{var2}","/f%20o_%20_f%7co","var1","f o","var2","f|o");
		checkCapture("/{var1}_ _{var2:f\\|o}","/f%20o_%20_f%7co","var1","f o","var2","f|o");
		checkCapture("/{var1:f o}_ _{var2:f\\|o}","/f%20o_%20_f%7co","var1","f o","var2","f|o");
	}
		
	@Test
	public void pathRemainingCornerCases_spr15336() {
		// No match when the literal path element is a longer form of the segment in the pattern
		assertNull(parse("/foo").getPathRemaining(toPathContainer("/footastic/bar")));
		assertNull(parse("/f?o").getPathRemaining(toPathContainer("/footastic/bar")));
		assertNull(parse("/f*o*p").getPathRemaining(toPathContainer("/flooptastic/bar")));
		assertNull(parse("/{abc}abc").getPathRemaining(toPathContainer("/xyzabcbar/bar")));

		// With a /** on the end have to check if there is any more data post
		// 'the match' it starts with a separator
		assertNull(parse("/resource/**").getPathRemaining(toPathContainer("/resourceX")));
		assertEquals("",parse("/resource/**").getPathRemaining(toPathContainer("/resource")).getPathRemaining().value());

		// Similar to above for the capture-the-rest variant
		assertNull(parse("/resource/{*foo}").getPathRemaining(toPathContainer("/resourceX")));
		assertEquals("",parse("/resource/{*foo}").getPathRemaining(toPathContainer("/resource")).getPathRemaining().value());

		PathPattern.PathRemainingMatchInfo pri = parse("/aaa/{bbb}/c?d/e*f/*/g").getPathRemaining(toPathContainer("/aaa/b/ccd/ef/x/g/i"));
		assertNotNull(pri);
		assertEquals("/i",pri.getPathRemaining().value());
		assertEquals("b",pri.getUriVariables().get("bbb"));

		pri = parse("/aaa/{bbb}/c?d/e*f/*/g/").getPathRemaining(toPathContainer("/aaa/b/ccd/ef/x/g/i"));
		assertNotNull(pri);
		assertEquals("i",pri.getPathRemaining().value());
		assertEquals("b",pri.getUriVariables().get("bbb"));
		
		pri = parse("/{aaa}_{bbb}/e*f/{x}/g").getPathRemaining(toPathContainer("/aa_bb/ef/x/g/i"));
		assertNotNull(pri);
		assertEquals("/i",pri.getPathRemaining().value());
		assertEquals("aa",pri.getUriVariables().get("aaa"));
		assertEquals("bb",pri.getUriVariables().get("bbb"));
		assertEquals("x",pri.getUriVariables().get("x"));

		assertNull(parse("/a/b").getPathRemaining(toPathContainer("")));
		assertEquals("/a/b",parse("").getPathRemaining(toPathContainer("/a/b")).getPathRemaining().value());
		assertEquals("",parse("").getPathRemaining(toPathContainer("")).getPathRemaining().value());
	}

	@Test
	public void questionMarks() {
		checkNoMatch("a", "ab");
		checkMatches("/f?o/bar", "/foo/bar");
		checkNoMatch("/foo/b2r", "/foo/bar");
		checkNoMatch("?", "te");
		checkMatches("?", "a");
		checkMatches("???", "abc");
		checkNoMatch("tes?", "te");
		checkNoMatch("tes?", "tes");
		checkNoMatch("tes?", "testt");
		checkNoMatch("tes?", "tsst");
		checkMatches(".?.a", ".a.a");
		checkNoMatch(".?.a", ".aba");
		checkMatches("/f?o/bar","/f%20o/bar");
	}

	@Test
	public void captureTheRest() {
		checkMatches("/resource/{*foobar}", "/resource");
		checkNoMatch("/resource/{*foobar}", "/resourceX");
		checkNoMatch("/resource/{*foobar}", "/resourceX/foobar");
		checkMatches("/resource/{*foobar}", "/resource/foobar");
		checkCapture("/resource/{*foobar}", "/resource/foobar", "foobar", "/foobar");
		checkCapture("/customer/{*something}", "/customer/99", "something", "/99");
		checkCapture("/customer/{*something}", "/customer/aa/bb/cc", "something",
				"/aa/bb/cc");
		checkCapture("/customer/{*something}", "/customer/", "something", "/");
		checkCapture("/customer/////{*something}", "/customer/////", "something", "/");
		checkCapture("/customer/////{*something}", "/customer//////", "something", "//");
		checkCapture("/customer//////{*something}", "/customer//////99", "something", "/99");
		checkCapture("/customer//////{*something}", "/customer//////99", "something", "/99");
		checkCapture("/customer/{*something}", "/customer", "something", "");
		checkCapture("/{*something}", "", "something", "");
		checkCapture("/customer/{*something}", "/customer//////99", "something", "//////99");
	}

	@Test
	public void multipleSeparatorsInPattern() {
		PathPattern pp = parse("a//b//c");
		assertEquals("Literal(a) Separator(/) Separator(/) Literal(b) Separator(/) Separator(/) Literal(c)",pp.toChainString());
		assertMatches(pp,"a//b//c");
		assertEquals("Literal(a) Separator(/) WildcardTheRest(/**)",parse("a//**").toChainString());
		checkMatches("///abc", "///abc");
		checkNoMatch("///abc", "/abc");
		checkNoMatch("//", "/");
		checkMatches("//", "//");
		checkNoMatch("///abc//d/e", "/abc/d/e");
		checkMatches("///abc//d/e", "///abc//d/e");
		checkNoMatch("///abc//{def}//////xyz", "/abc/foo/xyz");
		checkMatches("///abc//{def}//////xyz", "///abc//p//////xyz");
	}

	@Test
	public void multipleSelectorsInPath() {
		checkNoMatch("/abc", "////abc");
		checkNoMatch("/", "//");
		checkNoMatch("/abc/def/ghi", "/abc//def///ghi");
		checkNoMatch("/abc", "////abc");
		checkMatches("////abc", "////abc");
		checkNoMatch("/", "//");
		checkNoMatch("/abc//def", "/abc/def");
		checkNoMatch("/abc//def///ghi", "/abc/def/ghi");
		checkMatches("/abc//def///ghi", "/abc//def///ghi");
	}

	@Test
	public void multipleSeparatorsInPatternAndPath() {
		checkNoMatch("///one///two///three", "//one/////two///////three");
		checkMatches("//one/////two///////three", "//one/////two///////three");
		checkNoMatch("//one//two//three", "/one/////two/three");
		checkMatches("/one/////two/three", "/one/////two/three");
		checkCapture("///{foo}///bar", "///one///bar", "foo", "one");
	}
	
	@Test
	public void wildcards() {
		checkMatches("/*/bar", "/foo/bar");
		checkNoMatch("/*/bar", "/foo/baz");
		checkNoMatch("/*/bar", "//bar");
		checkMatches("/f*/bar", "/foo/bar");
		checkMatches("/*/bar", "/foo/bar");
		checkMatches("a/*","a/");
		checkMatches("/*","/");
		checkMatches("/*/bar", "/foo/bar");
		checkNoMatch("/*/bar", "/foo/baz");
		checkMatches("/f*/bar", "/foo/bar");
		checkMatches("/*/bar", "/foo/bar");
		checkMatches("/a*b*c*d/bar", "/abcd/bar");
		checkMatches("*a*", "testa");
		checkMatches("a/*", "a/");
		checkNoMatch("a/*", "a//"); // no data for *
		checkMatches("a/*", "a/a/"); // trailing slash, so is allowed
		PathPatternParser ppp = new PathPatternParser();
		ppp.setMatchOptionalTrailingSlash(false);
		assertFalse(ppp.parse("a/*").matches(toPathContainer("a//")));
		checkMatches("a/*", "a/a");
		checkMatches("a/*", "a/a/"); // trailing slash is optional
		checkMatches("/resource/**", "/resource");
		checkNoMatch("/resource/**", "/resourceX");
		checkNoMatch("/resource/**", "/resourceX/foobar");
		checkMatches("/resource/**", "/resource/foobar");
	}

	@Test
	public void constrainedMatches() {
		checkCapture("{foo:[0-9]*}", "123", "foo", "123");
		checkNoMatch("{foo:[0-9]*}", "abc");
		checkNoMatch("/{foo:[0-9]*}", "abc");
		checkCapture("/*/{foo:....}/**", "/foo/barg/foo", "foo", "barg");
		checkCapture("/*/{foo:....}/**", "/foo/barg/abc/def/ghi", "foo", "barg");
		checkNoMatch("{foo:....}", "99");
		checkMatches("{foo:..}", "99");
		checkCapture("/{abc:\\{\\}}", "/{}", "abc", "{}");
		checkCapture("/{abc:\\[\\]}", "/[]", "abc", "[]");
		checkCapture("/{abc:\\\\\\\\}", "/\\\\"); // this is fun...
	}

	@Test
	public void antPathMatcherTests() {
		// test exact matching
		checkMatches("test", "test");
		checkMatches("/test", "/test");
		checkMatches("http://example.org", "http://example.org");
		checkNoMatch("/test.jpg", "test.jpg");
		checkNoMatch("test", "/test");
		checkNoMatch("/test", "test");

		// test matching with ?'s
		checkMatches("t?st", "test");
		checkMatches("??st", "test");
		checkMatches("tes?", "test");
		checkMatches("te??", "test");
		checkMatches("?es?", "test");
		checkNoMatch("tes?", "tes");
		checkNoMatch("tes?", "testt");
		checkNoMatch("tes?", "tsst");

		// test matching with *'s
		checkMatches("*", "test");
		checkMatches("test*", "test");
		checkMatches("test*", "testTest");
		checkMatches("test/*", "test/Test");
		checkMatches("test/*", "test/t");
		checkMatches("test/*", "test/");
		checkMatches("*test*", "AnothertestTest");
		checkMatches("*test", "Anothertest");
		checkMatches("*.*", "test.");
		checkMatches("*.*", "test.test");
		checkMatches("*.*", "test.test.test");
		checkMatches("test*aaa", "testblaaaa");
		checkNoMatch("test*", "tst");
		checkNoMatch("test*", "tsttest");
		checkMatches("test*", "test/"); // trailing slash is optional
		checkMatches("test*", "test"); // trailing slash is optional
		checkNoMatch("test*", "test/t");
		checkNoMatch("test/*", "test");
		checkNoMatch("*test*", "tsttst");
		checkNoMatch("*test", "tsttst");
		checkNoMatch("*.*", "tsttst");
		checkNoMatch("test*aaa", "test");
		checkNoMatch("test*aaa", "testblaaab");

		// test matching with ?'s and /'s
		checkMatches("/?", "/a");
		checkMatches("/?/a", "/a/a");
		checkMatches("/a/?", "/a/b");
		checkMatches("/??/a", "/aa/a");
		checkMatches("/a/??", "/a/bb");
		checkMatches("/?", "/a");

		checkMatches("/**", "");
		checkMatches("/books/**", "/books");
		checkMatches("/**", "/testing/testing");
		checkMatches("/*/**", "/testing/testing");
		checkMatches("/bla*bla/test", "/blaXXXbla/test");
		checkMatches("/*bla/test", "/XXXbla/test");
		checkNoMatch("/bla*bla/test", "/blaXXXbl/test");
		checkNoMatch("/*bla/test", "XXXblab/test");
		checkNoMatch("/*bla/test", "XXXbl/test");
		checkNoMatch("/????", "/bala/bla");
		checkMatches("/foo/bar/**", "/foo/bar/");
		checkMatches("/{bla}.html", "/testing.html");
		checkCapture("/{bla}.*", "/testing.html", "bla", "testing");
	}

	@Test
	public void pathRemainingEnhancements_spr15419() {
		PathPattern pp;
		PathPattern.PathRemainingMatchInfo pri;
		// It would be nice to partially match a path and get any bound variables in one step
		pp = parse("/{this}/{one}/{here}");
		pri = getPathRemaining(pp, "/foo/bar/goo/boo");
		assertEquals("/boo",pri.getPathRemaining().value());
		assertEquals("foo",pri.getUriVariables().get("this"));
		assertEquals("bar",pri.getUriVariables().get("one"));
		assertEquals("goo",pri.getUriVariables().get("here"));
		
		pp = parse("/aaa/{foo}");
		pri = getPathRemaining(pp, "/aaa/bbb");
		assertEquals("",pri.getPathRemaining().value());
		assertEquals("bbb",pri.getUriVariables().get("foo"));

		pp = parse("/aaa/bbb");
		pri = getPathRemaining(pp, "/aaa/bbb");
		assertEquals("",pri.getPathRemaining().value());
		assertEquals(0,pri.getUriVariables().size());
		
		pp = parse("/*/{foo}/b*");
		pri = getPathRemaining(pp, "/foo");
		assertNull(pri);
		pri = getPathRemaining(pp, "/abc/def/bhi");
		assertEquals("",pri.getPathRemaining().value());
		assertEquals("def",pri.getUriVariables().get("foo"));

		pri = getPathRemaining(pp, "/abc/def/bhi/jkl");
		assertEquals("/jkl",pri.getPathRemaining().value());
		assertEquals("def",pri.getUriVariables().get("foo"));
	}
	
	@Test
	public void matchStart() {
		PathPatternParser ppp = new PathPatternParser();
		ppp.setMatchOptionalTrailingSlash(false);
		PathPattern pp = ppp.parse("test");
		assertFalse(pp.matchStart(PathContainer.parsePath("test/")));
		
		checkStartNoMatch("test/*/","test//");
		checkStartMatches("test/*","test/abc");
		checkStartMatches("test/*/def","test/abc/def");
		checkStartNoMatch("test/*/def","test//");
		checkStartNoMatch("test/*/def","test//def");
		
		checkStartMatches("test/{a}_{b}/foo", "test/a_b");
		checkStartMatches("test/?/abc", "test/a");
		checkStartMatches("test/{*foobar}", "test/");
		checkStartMatches("test/*/bar", "test/a");
		checkStartMatches("test/{foo}/bar", "test/abc");
		checkStartMatches("test//foo", "test//");
		checkStartMatches("test/foo", "test/");
		checkStartMatches("test/*", "test/");
		checkStartMatches("test", "test");
		checkStartNoMatch("test", "tes");
		checkStartMatches("test/", "test");

		// test exact matching
		checkStartMatches("test", "test");
		checkStartMatches("/test", "/test");
		checkStartNoMatch("/test.jpg", "test.jpg");
		checkStartNoMatch("test", "/test");
		checkStartNoMatch("/test", "test");

		// test matching with ?'s
		checkStartMatches("t?st", "test");
		checkStartMatches("??st", "test");
		checkStartMatches("tes?", "test");
		checkStartMatches("te??", "test");
		checkStartMatches("?es?", "test");
		checkStartNoMatch("tes?", "tes");
		checkStartNoMatch("tes?", "testt");
		checkStartNoMatch("tes?", "tsst");

		// test matching with *'s
		checkStartMatches("*", "test");
		checkStartMatches("test*", "test");
		checkStartMatches("test*", "testTest");
		checkStartMatches("test/*", "test/Test");
		checkStartMatches("test/*", "test/t");
		checkStartMatches("test/*", "test/");
		checkStartMatches("*test*", "AnothertestTest");
		checkStartMatches("*test", "Anothertest");
		checkStartMatches("*.*", "test.");
		checkStartMatches("*.*", "test.test");
		checkStartMatches("*.*", "test.test.test");
		checkStartMatches("test*aaa", "testblaaaa");
		checkStartNoMatch("test*", "tst");
		checkStartMatches("test*", "test/"); // trailing slash is optional
		checkStartMatches("test*", "test");
		checkStartNoMatch("test*", "tsttest");
		checkStartNoMatch("test*", "test/t");
		checkStartMatches("test/*", "test");
		checkStartMatches("test/t*.txt", "test");
		checkStartNoMatch("*test*", "tsttst");
		checkStartNoMatch("*test", "tsttst");
		checkStartNoMatch("*.*", "tsttst");
		checkStartNoMatch("test*aaa", "test");
		checkStartNoMatch("test*aaa", "testblaaab");

		// test matching with ?'s and /'s
		checkStartMatches("/?", "/a");
		checkStartMatches("/?/a", "/a/a");
		checkStartMatches("/a/?", "/a/b");
		checkStartMatches("/??/a", "/aa/a");
		checkStartMatches("/a/??", "/a/bb");
		checkStartMatches("/?", "/a");

		checkStartMatches("/**", "/testing/testing");
		checkStartMatches("/*/**", "/testing/testing");
		checkStartMatches("test*/**", "test/");
		checkStartMatches("test*/**", "test/t");
		checkStartMatches("/bla*bla/test", "/blaXXXbla/test");
		checkStartMatches("/*bla/test", "/XXXbla/test");
		checkStartNoMatch("/bla*bla/test", "/blaXXXbl/test");
		checkStartNoMatch("/*bla/test", "XXXblab/test");
		checkStartNoMatch("/*bla/test", "XXXbl/test");

		checkStartNoMatch("/????", "/bala/bla");

		checkStartMatches("/*bla*/*/bla/**",
				"/XXXblaXXXX/testing/bla/testing/testing/");
		checkStartMatches("/*bla*/*/bla/*",
				"/XXXblaXXXX/testing/bla/testing");
		checkStartMatches("/*bla*/*/bla/**",
				"/XXXblaXXXX/testing/bla/testing/testing");
		checkStartMatches("/*bla*/*/bla/**",
				"/XXXblaXXXX/testing/bla/testing/testing.jpg");

		checkStartMatches("/abc/{foo}", "/abc/def");
		checkStartMatches("/abc/{foo}", "/abc/def/"); // trailing slash is optional
		checkStartMatches("/abc/{foo}/", "/abc/def/");
		checkStartNoMatch("/abc/{foo}/", "/abc/def/ghi");
		checkStartMatches("/abc/{foo}/", "/abc/def");

		checkStartMatches("", "");
		checkStartMatches("", null);
		checkStartMatches("/abc", null);
	}

	@Test
	public void caseSensitivity() {
		PathPatternParser pp = new PathPatternParser();
		pp.setCaseSensitive(false);
		PathPattern p = pp.parse("abc");
		assertMatches(p,"AbC");
		assertNoMatch(p,"def");
		p = pp.parse("fOo");
		assertMatches(p,"FoO");
		p = pp.parse("/fOo/bAr");
		assertMatches(p,"/FoO/BaR");

		pp = new PathPatternParser();
		pp.setCaseSensitive(true);
		p = pp.parse("abc");
		assertNoMatch(p,"AbC");
		p = pp.parse("fOo");
		assertNoMatch(p,"FoO");
		p = pp.parse("/fOo/bAr");
		assertNoMatch(p,"/FoO/BaR");
		p = pp.parse("/fOO/bAr");
		assertMatches(p,"/fOO/bAr");

		pp = new PathPatternParser();
		pp.setCaseSensitive(false);
		p = pp.parse("{foo:[A-Z]*}");
		assertMatches(p,"abc");
		assertMatches(p,"ABC");

		pp = new PathPatternParser();
		pp.setCaseSensitive(true);
		p = pp.parse("{foo:[A-Z]*}");
		assertNoMatch(p,"abc");
		assertMatches(p,"ABC");

		pp = new PathPatternParser();
		pp.setCaseSensitive(false);
		p = pp.parse("ab?");
		assertMatches(p,"AbC");
		p = pp.parse("fO?");
		assertMatches(p,"FoO");
		p = pp.parse("/fO?/bA?");
		assertMatches(p,"/FoO/BaR");
		assertNoMatch(p,"/bAr/fOo");

		pp = new PathPatternParser();
		pp.setCaseSensitive(true);
		p = pp.parse("ab?");
		assertNoMatch(p,"AbC");
		p = pp.parse("fO?");
		assertNoMatch(p,"FoO");
		p = pp.parse("/fO?/bA?");
		assertNoMatch(p,"/FoO/BaR");
		p = pp.parse("/fO?/bA?");
		assertMatches(p,"/fOO/bAr");

		pp = new PathPatternParser();
		pp.setCaseSensitive(false);
		p = pp.parse("{abc:[A-Z]*}_{def:[A-Z]*}");
		assertMatches(p,"abc_abc");
		assertMatches(p,"ABC_aBc");

		pp = new PathPatternParser();
		pp.setCaseSensitive(true);
		p = pp.parse("{abc:[A-Z]*}_{def:[A-Z]*}");
		assertNoMatch(p,"abc_abc");
		assertMatches(p,"ABC_ABC");

		pp = new PathPatternParser();
		pp.setCaseSensitive(false);
		p = pp.parse("*?a?*");
		assertMatches(p,"bab");
		assertMatches(p,"bAb");

		pp = new PathPatternParser();
		pp.setCaseSensitive(true);
		p = pp.parse("*?A?*");
		assertNoMatch(p,"bab");
		assertMatches(p,"bAb");
	}

	@Ignore
	@Test
	public void alternativeDelimiter() {
		try {
			this.separator = '.';

			// test exact matching
//			checkMatches("test", "test");
//			checkMatches(".test", ".test");
//			checkNoMatch(".test/jpg", "test/jpg");
//			checkNoMatch("test", ".test");
//			checkNoMatch(".test", "test");

			// test matching with ?'s
			checkMatches("t?st", "test");
			checkMatches("??st", "test");
			checkMatches("tes?", "test");
			checkMatches("te??", "test");
			checkMatches("?es?", "test");
			checkNoMatch("tes?", "tes");
			checkNoMatch("tes?", "testt");
			checkNoMatch("tes?", "tsst");

			// test matching with *'s
			checkMatches("*", "test");
			checkMatches("test*", "test");
			checkMatches("test*", "testTest");
			checkMatches("*test*", "AnothertestTest");
			checkMatches("*test", "Anothertest");
			checkMatches("*/*", "test/");
			checkMatches("*/*", "test/test");
			checkMatches("*/*", "test/test/test");
			checkMatches("test*aaa", "testblaaaa");
			checkNoMatch("test*", "tst");
			checkNoMatch("test*", "tsttest");
			checkNoMatch("*test*", "tsttst");
			checkNoMatch("*test", "tsttst");
			checkNoMatch("*/*", "tsttst");
			checkNoMatch("test*aaa", "test");
			checkNoMatch("test*aaa", "testblaaab");

			// test matching with ?'s and .'s
			checkMatches(".?", ".a");
			checkMatches(".?.a", ".a.a");
			checkMatches(".a.?", ".a.b");
			checkMatches(".??.a", ".aa.a");
			checkMatches(".a.??", ".a.bb");
			checkMatches(".?", ".a");

			// test matching with **'s
			checkMatches(".**", ".testing.testing");
			checkMatches(".*.**", ".testing.testing");
			checkMatches(".bla*bla.test", ".blaXXXbla.test");
			checkMatches(".*bla.test", ".XXXbla.test");
			checkNoMatch(".bla*bla.test", ".blaXXXbl.test");
			checkNoMatch(".*bla.test", "XXXblab.test");
			checkNoMatch(".*bla.test", "XXXbl.test");
		}
		finally {
			this.separator = PathPatternParser.DEFAULT_SEPARATOR;
		}
	}

	@Test
	public void extractPathWithinPattern_spr15259() { 
		checkExtractPathWithinPattern("/**","//","/");
		checkExtractPathWithinPattern("/**","/","");
		checkExtractPathWithinPattern("/**","","");
		checkExtractPathWithinPattern("/**","/foobar","foobar");
	}
	
	@Test
	public void extractPathWithinPattern() throws Exception {
		checkExtractPathWithinPattern("/welcome*/", "/welcome/", "welcome");
		checkExtractPathWithinPattern("/docs/commit.html", "/docs/commit.html", "");
		checkExtractPathWithinPattern("/docs/*", "/docs/cvs/commit", "cvs/commit");
		checkExtractPathWithinPattern("/docs/cvs/*.html", "/docs/cvs/commit.html", "commit.html");
		checkExtractPathWithinPattern("/docs/**", "/docs/cvs/commit", "cvs/commit");
		checkExtractPathWithinPattern("/doo/{*foobar}", "/doo/customer.html", "customer.html");
		checkExtractPathWithinPattern("/doo/{*foobar}", "/doo/daa/customer.html", "daa/customer.html");
		checkExtractPathWithinPattern("/*.html", "/commit.html", "commit.html");
		checkExtractPathWithinPattern("/docs/*/*/*/*", "/docs/cvs/other/commit.html", "cvs/other/commit.html");
		checkExtractPathWithinPattern("/d?cs/**", "/docs/cvs/commit", "docs/cvs/commit");
		checkExtractPathWithinPattern("/docs/c?s/*.html", "/docs/cvs/commit.html", "cvs/commit.html");
		checkExtractPathWithinPattern("/d?cs/*/*.html", "/docs/cvs/commit.html", "docs/cvs/commit.html");
		checkExtractPathWithinPattern("/a/b/c*d*/*.html", "/a/b/cod/foo.html", "cod/foo.html");
		checkExtractPathWithinPattern("a/{foo}/b/{bar}", "a/c/b/d", "c/b/d");
		checkExtractPathWithinPattern("a/{foo}_{bar}/d/e", "a/b_c/d/e", "b_c/d/e");
		checkExtractPathWithinPattern("aaa//*///ccc///ddd", "aaa//bbb///ccc///ddd", "bbb/ccc/ddd");
		checkExtractPathWithinPattern("aaa//*///ccc///ddd", "aaa//bbb//ccc/ddd", "bbb/ccc/ddd");
		checkExtractPathWithinPattern("aaa/c*/ddd/", "aaa/ccc///ddd///", "ccc/ddd");
		checkExtractPathWithinPattern("", "", "");
		checkExtractPathWithinPattern("/", "", "");
		checkExtractPathWithinPattern("", "/", "");
		checkExtractPathWithinPattern("//", "", "");
		checkExtractPathWithinPattern("", "//", "");
		checkExtractPathWithinPattern("//", "//", "");
		checkExtractPathWithinPattern("//", "/", "");
		checkExtractPathWithinPattern("/", "//", "");
	}

	@Test
	public void extractUriTemplateVariables_spr15264() {
		PathPattern pp;
		pp = new PathPatternParser().parse("/{foo}");
		assertMatches(pp,"/abc");
		assertNoMatch(pp,"/");
		assertNoMatch(pp,"//");
		checkCapture("/{foo}", "/abc", "foo", "abc");
		
		pp = new PathPatternParser().parse("/{foo}/{bar}");
		assertMatches(pp,"/abc/def");
		assertNoMatch(pp,"/def");
		assertNoMatch(pp,"/");
		assertNoMatch(pp,"//def");
		assertNoMatch(pp,"//");
		
		pp = parse("/{foo}/boo");
		assertMatches(pp,"/abc/boo");
		assertMatches(pp,"/a/boo");
		assertNoMatch(pp,"/boo");
		assertNoMatch(pp,"//boo");

		pp = parse("/{foo}*");
		assertMatches(pp,"/abc");
		assertNoMatch(pp,"/");

		checkCapture("/{word:[a-z]*}", "/abc", "word", "abc");
		pp = parse("/{word:[a-z]*}");
		assertNoMatch(pp,"/1");
		assertMatches(pp,"/a");
		assertNoMatch(pp,"/");
		
		// Two captures mean we use a RegexPathElement
		pp = new PathPatternParser().parse("/{foo}{bar}");
		assertMatches(pp,"/abcdef");
		assertNoMatch(pp,"/");
		assertNoMatch(pp,"//");
		checkCapture("/{foo:[a-z][a-z]}{bar:[a-z]}", "/abc", "foo", "ab", "bar", "c");
		
		// Only patterns not capturing variables cannot match against just /
		PathPatternParser ppp = new PathPatternParser();
		ppp.setMatchOptionalTrailingSlash(true);
		pp = ppp.parse("/****");
		assertMatches(pp,"/abcdef");
		assertMatches(pp,"/");
		assertMatches(pp,"/");
		assertMatches(pp,"//");

		// Confirming AntPathMatcher behaviour:
		assertFalse(new AntPathMatcher().match("/{foo}", "/"));
		assertTrue(new AntPathMatcher().match("/{foo}", "/a"));
		assertTrue(new AntPathMatcher().match("/{foo}{bar}", "/a"));
		assertFalse(new AntPathMatcher().match("/{foo}*", "/"));
		assertTrue(new AntPathMatcher().match("/*", "/"));
		assertFalse(new AntPathMatcher().match("/*{foo}", "/"));
		Map<String, String> vars = new AntPathMatcher().extractUriTemplateVariables("/{foo}{bar}", "/a");
		assertEquals("a",vars.get("foo"));
		assertEquals("",vars.get("bar"));
	}
	
	@Test
	public void extractUriTemplateVariables() throws Exception {
		assertMatches(parse("{hotel}"),"1");
		assertMatches(parse("/hotels/{hotel}"),"/hotels/1");
		checkCapture("/hotels/{hotel}", "/hotels/1", "hotel", "1");
		checkCapture("/h?tels/{hotel}", "/hotels/1", "hotel", "1");
		checkCapture("/hotels/{hotel}/bookings/{booking}", "/hotels/1/bookings/2", "hotel", "1", "booking", "2");
		checkCapture("/*/hotels/*/{hotel}", "/foo/hotels/bar/1", "hotel", "1");
		checkCapture("/{page}.html", "/42.html", "page", "42");
		checkNoMatch("/{var}","/");
		checkCapture("/{page}.*", "/42.html", "page", "42");
		checkCapture("/A-{B}-C", "/A-b-C", "B", "b");
		checkCapture("/{name}.{extension}", "/test.html", "name", "test", "extension", "html");
		try {
			checkCapture("/{one}/", "//", "one", "");
			fail("Expected exception");
		}
		catch (IllegalStateException e) {
			assertEquals("Pattern \"/{one}/\" is not a match for \"//\"", e.getMessage());
		}
		try {
			checkCapture("", "/abc");
			fail("Expected exception");
		}
		catch (IllegalStateException e) {
			assertEquals("Pattern \"\" is not a match for \"/abc\"", e.getMessage());
		}
		assertEquals(0, checkCapture("", "").getUriVariables().size());
		checkCapture("{id}", "99", "id", "99");
		checkCapture("/customer/{customerId}", "/customer/78", "customerId", "78");
		checkCapture("/customer/{customerId}/banana", "/customer/42/banana", "customerId",
				"42");
		checkCapture("{id}/{id2}", "99/98", "id", "99", "id2", "98");
		checkCapture("/foo/{bar}/boo/{baz}", "/foo/plum/boo/apple", "bar", "plum", "baz",
				"apple");
		checkCapture("/{bla}.*", "/testing.html", "bla", "testing");
		PathMatchResult extracted = checkCapture("/abc", "/abc");
		assertEquals(0, extracted.getUriVariables().size());
		checkCapture("/{bla}/foo","/a/foo");
	}

	@Test
	public void extractUriTemplateVariablesRegex() {
		PathPatternParser pp = new PathPatternParser();
		PathPattern p = null;

		p = pp.parse("{symbolicName:[\\w\\.]+}-{version:[\\w\\.]+}.jar");
		PathMatchResult result = matchAndExtract(p, "com.example-1.0.0.jar");
		assertEquals("com.example", result.getUriVariables().get("symbolicName"));
		assertEquals("1.0.0", result.getUriVariables().get("version"));

		p = pp.parse("{symbolicName:[\\w\\.]+}-sources-{version:[\\w\\.]+}.jar");
		result = matchAndExtract(p, "com.example-sources-1.0.0.jar");
		assertEquals("com.example", result.getUriVariables().get("symbolicName"));
		assertEquals("1.0.0", result.getUriVariables().get("version"));
	}

	@Test
	public void extractUriTemplateVarsRegexQualifiers() {
		PathPatternParser pp = new PathPatternParser();

		PathPattern p = pp.parse("{symbolicName:[\\p{L}\\.]+}-sources-{version:[\\p{N}\\.]+}.jar");
		PathMatchResult result = p.matchAndExtract(toPathContainer("com.example-sources-1.0.0.jar"));
		assertEquals("com.example", result.getUriVariables().get("symbolicName"));
		assertEquals("1.0.0", result.getUriVariables().get("version"));

		p = pp.parse("{symbolicName:[\\w\\.]+}-sources-{version:[\\d\\.]+}-{year:\\d{4}}{month:\\d{2}}{day:\\d{2}}.jar");
		result = matchAndExtract(p,"com.example-sources-1.0.0-20100220.jar");
		assertEquals("com.example", result.getUriVariables().get("symbolicName"));
		assertEquals("1.0.0", result.getUriVariables().get("version"));
		assertEquals("2010", result.getUriVariables().get("year"));
		assertEquals("02", result.getUriVariables().get("month"));
		assertEquals("20", result.getUriVariables().get("day"));

		p = pp.parse("{symbolicName:[\\p{L}\\.]+}-sources-{version:[\\p{N}\\.\\{\\}]+}.jar");
		result = matchAndExtract(p, "com.example-sources-1.0.0.{12}.jar");
		assertEquals("com.example", result.getUriVariables().get("symbolicName"));
		assertEquals("1.0.0.{12}", result.getUriVariables().get("version"));
	}

	@Test
	public void extractUriTemplateVarsRegexCapturingGroups() {
		PathPatternParser ppp = new PathPatternParser();
		PathPattern pathPattern = ppp.parse("/web/{id:foo(bar)?}_{goo}");
		exception.expect(IllegalArgumentException.class);
		exception.expectMessage(containsString("The number of capturing groups in the pattern"));
		matchAndExtract(pathPattern,"/web/foobar_goo");
	}

	@Rule
	public final ExpectedException exception = ExpectedException.none();

	@Test
	public void combine() {
		TestPathCombiner pathMatcher = new TestPathCombiner();
		assertEquals("", pathMatcher.combine("", ""));
		assertEquals("/hotels", pathMatcher.combine("/hotels", ""));
		assertEquals("/hotels", pathMatcher.combine("", "/hotels"));
		assertEquals("/hotels/booking", pathMatcher.combine("/hotels/*", "booking"));
		assertEquals("/hotels/booking", pathMatcher.combine("/hotels/*", "/booking"));
		assertEquals("/hotels/**/booking", pathMatcher.combine("/hotels/**", "booking"));
		assertEquals("/hotels/**/booking", pathMatcher.combine("/hotels/**", "/booking"));
		assertEquals("/hotels/booking", pathMatcher.combine("/hotels", "/booking"));
		assertEquals("/hotels/booking", pathMatcher.combine("/hotels", "booking"));
		assertEquals("/hotels/booking", pathMatcher.combine("/hotels/", "booking"));
		assertEquals("/hotels/{hotel}", pathMatcher.combine("/hotels/*", "{hotel}"));
		assertEquals("/hotels/**/{hotel}", pathMatcher.combine("/hotels/**", "{hotel}"));
		assertEquals("/hotels/{hotel}", pathMatcher.combine("/hotels", "{hotel}"));
		assertEquals("/hotels/{hotel}.*", pathMatcher.combine("/hotels", "{hotel}.*"));
		assertEquals("/hotels/*/booking/{booking}",
				pathMatcher.combine("/hotels/*/booking", "{booking}"));
		assertEquals("/hotel.html", pathMatcher.combine("/*.html", "/hotel.html"));
		assertEquals("/hotel.html", pathMatcher.combine("/*.html", "/hotel"));
		assertEquals("/hotel.html", pathMatcher.combine("/*.html", "/hotel.*"));
		// TODO this seems rather bogus, should we eagerly show an error?
		assertEquals("/d/e/f/hotel.html", pathMatcher.combine("/a/b/c/*.html", "/d/e/f/hotel.*"));
		assertEquals("/*.html", pathMatcher.combine("/**", "/*.html"));
		assertEquals("/*.html", pathMatcher.combine("/*", "/*.html"));
		assertEquals("/*.html", pathMatcher.combine("/*.*", "/*.html"));
		assertEquals("/{foo}/bar", pathMatcher.combine("/{foo}", "/bar"));  // SPR-8858
		assertEquals("/user/user", pathMatcher.combine("/user", "/user"));  // SPR-7970
		assertEquals("/{foo:.*[^0-9].*}/edit/",
				pathMatcher.combine("/{foo:.*[^0-9].*}", "/edit/"));  // SPR-10062
		assertEquals("/1.0/foo/test", pathMatcher.combine("/1.0", "/foo/test"));
		// SPR-10554
		assertEquals("/hotel", pathMatcher.combine("/", "/hotel"));  // SPR-12975
		assertEquals("/hotel/booking", pathMatcher.combine("/hotel/", "/booking"));  // SPR-12975
		assertEquals("/hotel", pathMatcher.combine("", "/hotel"));
		assertEquals("/hotel", pathMatcher.combine("/hotel", ""));
		// TODO Do we need special handling when patterns contain multiple dots?
	}

	@Test
	public void combineWithTwoFileExtensionPatterns() {
		TestPathCombiner pathMatcher = new TestPathCombiner();
		exception.expect(IllegalArgumentException.class);
		pathMatcher.combine("/*.html", "/*.txt");
	}

	@Test
	public void patternComparator() {
		Comparator<PathPattern> comparator = new ParsingPathMatcher.PatternComparatorConsideringPath("/hotels/new");

		assertEquals(0, comparator.compare(null, null));
		assertEquals(1, comparator.compare(null, parse("/hotels/new")));
		assertEquals(-1, comparator.compare(parse("/hotels/new"), null));

		assertEquals(0, comparator.compare(parse("/hotels/new"), parse("/hotels/new")));

		assertEquals(-1, comparator.compare(parse("/hotels/new"), parse("/hotels/*")));
		assertEquals(1, comparator.compare(parse("/hotels/*"), parse("/hotels/new")));
		assertEquals(0, comparator.compare(parse("/hotels/*"), parse("/hotels/*")));

		assertEquals(-1,
				comparator.compare(parse("/hotels/new"), parse("/hotels/{hotel}")));
		assertEquals(1,
				comparator.compare(parse("/hotels/{hotel}"), parse("/hotels/new")));
		assertEquals(0,
				comparator.compare(parse("/hotels/{hotel}"), parse("/hotels/{hotel}")));
		assertEquals(-1, comparator.compare(parse("/hotels/{hotel}/booking"),
				parse("/hotels/{hotel}/bookings/{booking}")));
		assertEquals(1, comparator.compare(parse("/hotels/{hotel}/bookings/{booking}"),
				parse("/hotels/{hotel}/booking")));

		assertEquals(-1,
				comparator.compare(
						parse("/hotels/{hotel}/bookings/{booking}/cutomers/{customer}"),
						parse("/**")));
		assertEquals(1, comparator.compare(parse("/**"),
				parse("/hotels/{hotel}/bookings/{booking}/cutomers/{customer}")));
		assertEquals(0, comparator.compare(parse("/**"), parse("/**")));

		assertEquals(-1,
				comparator.compare(parse("/hotels/{hotel}"), parse("/hotels/*")));
		assertEquals(1, comparator.compare(parse("/hotels/*"), parse("/hotels/{hotel}")));

		assertEquals(-1, comparator.compare(parse("/hotels/*"), parse("/hotels/*/**")));
		assertEquals(1, comparator.compare(parse("/hotels/*/**"), parse("/hotels/*")));

		assertEquals(-1,
				comparator.compare(parse("/hotels/new"), parse("/hotels/new.*")));

		// SPR-6741
		assertEquals(-1,
				comparator.compare(
						parse("/hotels/{hotel}/bookings/{booking}/cutomers/{customer}"),
						parse("/hotels/**")));
		assertEquals(1, comparator.compare(parse("/hotels/**"),
				parse("/hotels/{hotel}/bookings/{booking}/cutomers/{customer}")));
		assertEquals(1, comparator.compare(parse("/hotels/foo/bar/**"),
				parse("/hotels/{hotel}")));
		assertEquals(-1, comparator.compare(parse("/hotels/{hotel}"),
				parse("/hotels/foo/bar/**")));

		// SPR-8683
		assertEquals(1, comparator.compare(parse("/**"), parse("/hotels/{hotel}")));

		// longer is better
		assertEquals(1, comparator.compare(parse("/hotels"), parse("/hotels2")));

		// SPR-13139
		assertEquals(-1, comparator.compare(parse("*"), parse("*/**")));
		assertEquals(1, comparator.compare(parse("*/**"), parse("*")));
	}
	
	@Test
	public void compare_spr15597() {
		PathPatternParser parser = new PathPatternParser();
		PathPattern p1 = parser.parse("/{foo}");
		PathPattern p2 = parser.parse("/{foo}.*");
		PathMatchResult r1 = matchAndExtract(p1, "/file.txt");
		PathMatchResult r2 = matchAndExtract(p2, "/file.txt");
		 
		// works fine
		assertEquals("file.txt", r1.getUriVariables().get("foo"));
		assertEquals("file", r2.getUriVariables().get("foo"));

		// This produces 2 (see comments in https://jira.spring.io/browse/SPR-14544 )
		// Comparator<String> patternComparator = new AntPathMatcher().getPatternComparator("");
		// System.out.println(patternComparator.compare("/{foo}","/{foo}.*"));

		assertThat(p1.compareTo(p2), Matchers.greaterThan(0));
	}

	@Test
	public void patternCompareTo() {
		PathPatternParser p = new PathPatternParser();
		PathPattern pp = p.parse("/abc");
		assertEquals(-1, pp.compareTo(null));
	}

	@Test
	public void patternComparatorSort() {
		Comparator<PathPattern> comparator = new ParsingPathMatcher.PatternComparatorConsideringPath("/hotels/new");

		List<PathPattern> paths = new ArrayList<>(3);
		PathPatternParser pp = new PathPatternParser();
		paths.add(null);
		paths.add(pp.parse("/hotels/new"));
		Collections.sort(paths, comparator);
		assertEquals("/hotels/new", paths.get(0).getPatternString());
		assertNull(paths.get(1));
		paths.clear();

		paths.add(pp.parse("/hotels/new"));
		paths.add(null);
		Collections.sort(paths, comparator);
		assertEquals("/hotels/new", paths.get(0).getPatternString());
		assertNull(paths.get(1));
		paths.clear();

		paths.add(pp.parse("/hotels/*"));
		paths.add(pp.parse("/hotels/new"));
		Collections.sort(paths, comparator);
		assertEquals("/hotels/new", paths.get(0).getPatternString());
		assertEquals("/hotels/*", paths.get(1).getPatternString());
		paths.clear();

		paths.add(pp.parse("/hotels/new"));
		paths.add(pp.parse("/hotels/*"));
		Collections.sort(paths, comparator);
		assertEquals("/hotels/new", paths.get(0).getPatternString());
		assertEquals("/hotels/*", paths.get(1).getPatternString());
		paths.clear();

		paths.add(pp.parse("/hotels/**"));
		paths.add(pp.parse("/hotels/*"));
		Collections.sort(paths, comparator);
		assertEquals("/hotels/*", paths.get(0).getPatternString());
		assertEquals("/hotels/**", paths.get(1).getPatternString());
		paths.clear();

		paths.add(pp.parse("/hotels/*"));
		paths.add(pp.parse("/hotels/**"));
		Collections.sort(paths, comparator);
		assertEquals("/hotels/*", paths.get(0).getPatternString());
		assertEquals("/hotels/**", paths.get(1).getPatternString());
		paths.clear();

		paths.add(pp.parse("/hotels/{hotel}"));
		paths.add(pp.parse("/hotels/new"));
		Collections.sort(paths, comparator);
		assertEquals("/hotels/new", paths.get(0).getPatternString());
		assertEquals("/hotels/{hotel}", paths.get(1).getPatternString());
		paths.clear();

		paths.add(pp.parse("/hotels/new"));
		paths.add(pp.parse("/hotels/{hotel}"));
		Collections.sort(paths, comparator);
		assertEquals("/hotels/new", paths.get(0).getPatternString());
		assertEquals("/hotels/{hotel}", paths.get(1).getPatternString());
		paths.clear();

		paths.add(pp.parse("/hotels/*"));
		paths.add(pp.parse("/hotels/{hotel}"));
		paths.add(pp.parse("/hotels/new"));
		Collections.sort(paths, comparator);
		assertEquals("/hotels/new", paths.get(0).getPatternString());
		assertEquals("/hotels/{hotel}", paths.get(1).getPatternString());
		assertEquals("/hotels/*", paths.get(2).getPatternString());
		paths.clear();

		paths.add(pp.parse("/hotels/ne*"));
		paths.add(pp.parse("/hotels/n*"));
		Collections.shuffle(paths);
		Collections.sort(paths, comparator);
		assertEquals("/hotels/ne*", paths.get(0).getPatternString());
		assertEquals("/hotels/n*", paths.get(1).getPatternString());
		paths.clear();

		// comparator = new PatternComparatorConsideringPath("/hotels/new.html");
		// paths.add(pp.parse("/hotels/new.*"));
		// paths.add(pp.parse("/hotels/{hotel}"));
		// Collections.shuffle(paths);
		// Collections.sort(paths, comparator);
		// assertEquals("/hotels/new.*", paths.get(0).toPatternString());
		// assertEquals("/hotels/{hotel}", paths.get(1).toPatternString());
		// paths.clear();

		comparator = new ParsingPathMatcher.PatternComparatorConsideringPath("/web/endUser/action/login.html");
		paths.add(pp.parse("/*/login.*"));
		paths.add(pp.parse("/*/endUser/action/login.*"));
		Collections.sort(paths, comparator);
		assertEquals("/*/endUser/action/login.*", paths.get(0).getPatternString());
		assertEquals("/*/login.*", paths.get(1).getPatternString());
		paths.clear();
	}

	@Test  // SPR-13286
	public void caseInsensitive() {
		PathPatternParser pp = new PathPatternParser();
		pp.setCaseSensitive(false);
		PathPattern p = pp.parse("/group/{groupName}/members");
		assertMatches(p,"/group/sales/members");
		assertMatches(p,"/Group/Sales/Members");
		assertMatches(p,"/group/Sales/members");
	}

	@Test
	public void parameters() {
		// CaptureVariablePathElement
		PathMatchResult result = matchAndExtract("/abc/{var}","/abc/one;two=three;four=five");
		assertEquals("one",result.getUriVariables().get("var"));
		assertEquals("three",result.getMatrixVariables().get("var").getFirst("two"));
		assertEquals("five",result.getMatrixVariables().get("var").getFirst("four"));
		// RegexPathElement
		result = matchAndExtract("/abc/{var1}_{var2}","/abc/123_456;a=b;c=d");
		assertEquals("123",result.getUriVariables().get("var1"));
		assertEquals("456",result.getUriVariables().get("var2"));
		// vars associated with second variable
		assertNull(result.getMatrixVariables().get("var1"));
		assertNull(result.getMatrixVariables().get("var1"));
		assertEquals("b",result.getMatrixVariables().get("var2").getFirst("a"));
		assertEquals("d",result.getMatrixVariables().get("var2").getFirst("c"));
		// CaptureTheRestPathElement
		result = matchAndExtract("/{*var}","/abc/123_456;a=b;c=d");
		assertEquals("/abc/123_456",result.getUriVariables().get("var"));
		assertEquals("b",result.getMatrixVariables().get("var").getFirst("a"));
		assertEquals("d",result.getMatrixVariables().get("var").getFirst("c"));
		result = matchAndExtract("/{*var}","/abc/123_456;a=b;c=d/789;a=e;f=g");
		assertEquals("/abc/123_456/789",result.getUriVariables().get("var"));
		assertEquals("[b, e]",result.getMatrixVariables().get("var").get("a").toString());
		assertEquals("d",result.getMatrixVariables().get("var").getFirst("c"));
		assertEquals("g",result.getMatrixVariables().get("var").getFirst("f"));

		result = matchAndExtract("/abc/{var}","/abc/one");
		assertEquals("one",result.getUriVariables().get("var"));
		assertNull(result.getMatrixVariables().get("var"));
	}


	private PathMatchResult matchAndExtract(String pattern, String path) {
		 return parse(pattern).matchAndExtract(PathPatternMatcherTests.toPathContainer(path));
	}
	
	private PathPattern parse(String path) {
		PathPatternParser pp = new PathPatternParser();
		pp.setMatchOptionalTrailingSlash(true);
		return pp.parse(path);
	}
	
	public static PathContainer toPathContainer(String path) {
		if (path == null) {
			return null;
		}
		return PathContainer.parseUrlPath(path);
	}
	
	private void checkMatches(String uriTemplate, String path) {
		PathPatternParser parser = new PathPatternParser(this.separator);
		parser.setMatchOptionalTrailingSlash(true);
		PathPattern p = parser.parse(uriTemplate);
		PathContainer pc = toPathContainer(path);
		assertTrue(p.matches(pc));
	}

	private void checkStartNoMatch(String uriTemplate, String path) {
		PathPatternParser p = new PathPatternParser();
		p.setMatchOptionalTrailingSlash(true);
		PathPattern pattern = p.parse(uriTemplate);
		assertFalse(pattern.matchStart(toPathContainer(path)));
	}

	private void checkStartMatches(String uriTemplate, String path) {
		PathPatternParser p = new PathPatternParser();
		p.setMatchOptionalTrailingSlash(true);
		PathPattern pattern = p.parse(uriTemplate);
		assertTrue(pattern.matchStart(toPathContainer(path)));
	}

	private void checkNoMatch(String uriTemplate, String path) {
		PathPatternParser p = new PathPatternParser();
		PathPattern pattern = p.parse(uriTemplate);
		PathContainer PathContainer = toPathContainer(path);
		assertFalse(pattern.matches(PathContainer));
	}

	private PathMatchResult checkCapture(String uriTemplate, String path, String... keyValues) {
		PathPatternParser parser = new PathPatternParser();
		PathPattern pattern = parser.parse(uriTemplate);
		PathMatchResult matchResult = pattern.matchAndExtract(toPathContainer(path));
		Map<String, String> expectedKeyValues = new HashMap<>();
		for (int i = 0; i < keyValues.length; i += 2) {
			expectedKeyValues.put(keyValues[i], keyValues[i + 1]);
		}
		for (Map.Entry<String, String> me : expectedKeyValues.entrySet()) {
			String value = matchResult.getUriVariables().get(me.getKey());
			if (value == null) {
				fail("Did not find key '" + me.getKey() + "' in captured variables: "
						+ matchResult.getUriVariables());
			}
			if (!value.equals(me.getValue())) {
				fail("Expected value '" + me.getValue() + "' for key '" + me.getKey()
						+ "' but was '" + value + "'");
			}
		}
		return matchResult;
	}

	private void checkExtractPathWithinPattern(String pattern, String path, String expected) {
		PathPatternParser ppp = new PathPatternParser();
		PathPattern pp = ppp.parse(pattern);
		String s = pp.extractPathWithinPattern(toPathContainer(path)).value();
		assertEquals(expected, s);
	}

	private PathRemainingMatchInfo getPathRemaining(String pattern, String path) {
		return parse(pattern).getPathRemaining(toPathContainer(path));
	}
	
	private PathRemainingMatchInfo getPathRemaining(PathPattern pattern, String path) {
		return pattern.getPathRemaining(toPathContainer(path));
	}
	
	private PathMatchResult matchAndExtract(PathPattern p, String path) {
		return p.matchAndExtract(toPathContainer(path));
	}

	private String elementsToString(List<Element> elements) {
		StringBuilder s = new StringBuilder();
		for (Element element: elements) {
			s.append("[").append(element.value()).append("]");
		}
		return s.toString();
	}


	static class TestPathCombiner {

		PathPatternParser pp = new PathPatternParser();

		public String combine(String string1, String string2) {
			PathPattern pattern1 = pp.parse(string1);
			PathPattern pattern2 = pp.parse(string2);
			return pattern1.combine(pattern2).getPatternString();
		}

	}

}
