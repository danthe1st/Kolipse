package io.github.danthe1st.kolipse.editor.display.highlighting;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.TextAttribute;
import org.eclipse.jface.text.TextPresentation;
import org.eclipse.jface.text.TextUtilities;
import org.eclipse.jface.text.presentation.IPresentationRepairer;
import org.eclipse.jface.text.presentation.PresentationReconciler;
import org.eclipse.jface.text.rules.DefaultDamagerRepairer;
import org.eclipse.jface.text.rules.IRule;
import org.eclipse.jface.text.rules.IToken;
import org.eclipse.jface.text.rules.MultiLineRule;
import org.eclipse.jface.text.rules.NumberRule;
import org.eclipse.jface.text.rules.PatternRule;
import org.eclipse.jface.text.rules.RuleBasedScanner;
import org.eclipse.jface.text.rules.SingleLineRule;
import org.eclipse.jface.text.rules.Token;
import org.eclipse.jface.text.rules.WhitespaceRule;
import org.eclipse.jface.text.rules.WordRule;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.widgets.Display;

public class KtPresentationReconciler extends PresentationReconciler {
	
	// https://kotlinlang.org/spec/syntax-and-grammar.html
	private static final Set<String> KEYWORDS = Collections.unmodifiableSet(
			new HashSet<>(
					Arrays.asList(
							"return", "continue", "break",
							"this", "super", "file",
							"field", "property", "get", "set", "receiver", "param", "setparam", "delegate",
							"package", "import", "class", "interface", "fun", "object",
							"val", "var", "typealias",
							"constructor",
							"by", "companion",
							"init", "typeof",
							"where", "if", "else", "when",
							"try", "catch", "finally", "for", "do", "while", "throw",
							"as", "is", "in", "out", "dynamic",
							"public", "private", "protected", "internal",
							"enum", "sealed", "annotation", "data", "inner",
							"tailrec", "operator", "inline", "infix", "external", "suspend",
							"override", "abstract", "final", "open",
							"const", "lateinit",
							"vararg", "noinline", "crossinline", "reified", "expect", "actual",
							
							"true", "false", "null"
					)
			)
	);
	
//    private final TextAttribute tagAttribute = new TextAttribute(new Color(Display.getCurrent(), new RGB(0,0, 255)));
//    private final TextAttribute headerAttribute = new TextAttribute(new Color(Display.getCurrent(), new RGB(128,128,128)));
	
	private IToken quoteToken = new Token(new TextAttribute(new Color(Display.getCurrent(), new RGB(0, 0, 255))));
	private IToken numberToken = new Token(new TextAttribute(new Color(Display.getCurrent(), new RGB(0, 0, 255))));
	private IToken commentToken = new Token(new TextAttribute(new Color(Display.getCurrent(), new RGB(0, 100, 0))));
	
	private IToken keywordToken = new Token(new TextAttribute(new Color(Display.getCurrent(), new RGB(127, 0, 85))));
	private IToken nonKeywordToken = new Token(new TextAttribute(new Color(Display.getCurrent(), new RGB(0, 0, 0))));
	
	public KtPresentationReconciler() {
		RuleBasedScanner scanner = createScanner();
		DefaultDamagerRepairer dr = new DefaultDamagerRepairer(scanner);
		this.setDamager(dr, IDocument.DEFAULT_CONTENT_TYPE);
		this.setRepairer(dr, IDocument.DEFAULT_CONTENT_TYPE);
	}
	
	public RuleBasedScanner createScanner() {
		RuleBasedScanner scanner = new RuleBasedScanner();
		IRule[] rules = new IRule[] {
				new MultiLineRule("/*", "*/", commentToken),
				new SingleLineRule("'", "'", quoteToken, '\\'),
				new SingleLineRule("\"", "\"", quoteToken, '\\'), // TODO ${}
				
				new PatternRule("//", null, commentToken, (char) 0, true),
				new PatternRule("#!", null, commentToken, (char) 0, true), // shebang
				new WhitespaceRule(
						c -> c == ' ' || c == '\n' || c == '\r' || c == '\u0020'
								|| c == '\t' || c == '\u0009' || c == '\u000c'
				),
				createKeywordAndIdentifierRule(),
				new NumberRule(numberToken) // TODO literals: e+-digits, 0xhexDigits, 0bbinDigits
		};
		scanner.setRules(rules);
		return scanner;
	}
	
	private IRule createKeywordAndIdentifierRule() {
		WordRule rule = new WordRule(new KeywordDetector(), nonKeywordToken);
		for(String keyword : KEYWORDS){
			rule.addWord(keyword, keywordToken);
		}
		return rule;
	}
	
	// https://bugs.eclipse.org/bugs/show_bug.cgi?id=520218
	// https://git.eclipse.org/c/pde/eclipse.pde.ui.git/commit/?id=b16cb60da1d5777255766cccec33baa347e237bc
	@Override
	protected TextPresentation createPresentation(IRegion damage, IDocument document) {
		TextPresentation presentation = new TextPresentation(damage, 1000);
		IPresentationRepairer repairer = this.getRepairer(IDocument.DEFAULT_CONTENT_TYPE);
		if(repairer != null)
			try{
				repairer.createPresentation(
						presentation, TextUtilities.computePartitioning(
								document,
								getDocumentPartitioning(), 0, document.getLength(), false
						)[0]
				);
			}catch(BadLocationException e){
				return null;
			}
		
		return presentation;
	}
}