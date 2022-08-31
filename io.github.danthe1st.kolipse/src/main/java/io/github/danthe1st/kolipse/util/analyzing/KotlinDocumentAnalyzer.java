package io.github.danthe1st.kolipse.util.analyzing;

import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.rules.IToken;
import org.eclipse.jface.text.rules.MultiLineRule;
import org.eclipse.jface.text.rules.PatternRule;
import org.eclipse.jface.text.rules.RuleBasedScanner;
import org.eclipse.jface.text.rules.Token;
import org.eclipse.jface.text.rules.WhitespaceRule;
import org.eclipse.jface.text.rules.WordRule;

import io.github.danthe1st.kolipse.editor.display.highlighting.KeywordDetector;

public final class KotlinDocumentAnalyzer {

	private final RuleBasedScanner scanner = new RuleBasedScanner();
	private static final IToken defaultWordToken = new Token(null);
	private static final IToken importToken = new Token(null);
	private static final IToken packageToken = new Token(null);
	private static final IToken commentToken = new Token(null);

	public KotlinDocumentAnalyzer() {
		WordRule importPackageRule = new WordRule(new KeywordDetector() {
			@Override
			public boolean isWordPart(char c) {
				return super.isWordPart(c) || c == '.';
			}
		}, defaultWordToken);
		importPackageRule.addWord("import", importToken);
		importPackageRule.addWord("package", packageToken);
		scanner.setRules(
				new MultiLineRule("/*", "*/", commentToken),
				new PatternRule("//", null, commentToken, (char) 0, true),
				importPackageRule,
				new PatternRule("package", null, importToken, (char) 0, true),
				new WhitespaceRule(Character::isWhitespace)
		);
	}

	public KotlinDocumentInfo analyzeKotlinDocument(IDocument doc) throws BadLocationException {
		scanner.setRange(doc, 0, doc.getLength());
		KotlinDocumentInfo info = new KotlinDocumentInfo();
		info.setDocumentLength(doc.getLength());
		IToken currentToken;
		while((currentToken = scanner.nextToken()) != Token.EOF){
			if(currentToken == packageToken){
				info.setPackageName(readNextWordToken(doc));
			}else if(currentToken == importToken){
				info.addImport(readNextWordToken(doc));
			}else if(currentToken == commentToken){
				info.addCommentRegion(scanner.getTokenOffset(), scanner.getTokenLength());
			}
		}
		return info;
	}

	private String readNextWordToken(IDocument doc) throws BadLocationException {
		while(scanner.nextToken() != defaultWordToken){
			// skip tokens
		}
		String packageName = getLastTokenContent(doc);
		return packageName;
	}

	private String getLastTokenContent(IDocument doc) throws BadLocationException {
		return doc.get(scanner.getTokenOffset(), scanner.getTokenLength());
	}
}
