package io.github.danthe1st.kolipse.editor.display.highlighting;

import org.eclipse.jface.text.rules.IWordDetector;

public class KeywordDetector implements IWordDetector {
	
	@Override
	public boolean isWordPart(char c) {
		return Character.isJavaIdentifierPart(c);
	}
	
	@Override
	public boolean isWordStart(char c) {
		return Character.isJavaIdentifierStart(c);
	}
}
