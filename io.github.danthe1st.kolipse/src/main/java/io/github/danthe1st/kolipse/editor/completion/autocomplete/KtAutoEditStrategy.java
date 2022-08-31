package io.github.danthe1st.kolipse.editor.completion.autocomplete;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.DocumentCommand;
import org.eclipse.jface.text.IAutoEditStrategy;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.ui.statushandlers.StatusManager;

import io.github.danthe1st.kolipse.util.analyzing.KotlinDocumentAnalyzer;
import io.github.danthe1st.kolipse.util.analyzing.KotlinDocumentInfo;

public class KtAutoEditStrategy implements IAutoEditStrategy {
	private String lastCommand = "";
	private KotlinDocumentAnalyzer analyzer = new KotlinDocumentAnalyzer();
	
	@Override
	public void customizeDocumentCommand(IDocument document, DocumentCommand command) {
		// TODO more autocomplete options
		try{
			KotlinDocumentInfo docInfo = analyzer.analyzeKotlinDocument(document);
			if(docInfo.isInComment(command.offset)){// TODO ignore in strings as well
				return;
			}
			command.caretOffset = command.offset;
			IRegion region = document.getLineInformationOfOffset(command.offset);
			command.shiftsCaret = false;
			command.caretOffset += command.text.length();
			String line = document.get(region.getOffset(), command.offset - region.getOffset());
			int commentIndex = line.indexOf("//");
			if(commentIndex != -1 && commentIndex < command.offset - region.getOffset()){
				return;
			}
			
			if(command.text.endsWith("\n")){
				String whitespace = getTrailingWhitespace(line);
				addBeforeCursor(command, whitespace);
				if("{".equals(lastCommand)){
					addBeforeCursor(command, "\t");
					String toInsert = getLineDelimiter(document) + whitespace + "}";
					document.replace(command.offset, 0, toInsert);
				}else if(lastCommand.endsWith("*") && command.offset > 2 && document.getChar(command.offset - 2) == '/'){
					addAfterCursor(command, getLineDelimiter(document) + whitespace + "*/");
				}
			}
		}catch(BadLocationException e){
			StatusManager.getManager().handle(new Status(IStatus.ERROR, getClass(), "An error occured trying to compute autocomplete", e));
		}
		lastCommand = command.text;
	}
	
	public static String getTrailingWhitespace(String line) {
		int i = 0;
		while(i < line.length() && Character.isWhitespace(line.charAt(i))){
			i++;
		}
		return line.substring(0, i);
	}
	
	private String getLineDelimiter(IDocument document) throws BadLocationException {
		String delimiter = document.getLineDelimiter(0);
		if(delimiter == null){
			return "\n";
		}
		return delimiter;
	}
	
	private void addBeforeCursor(DocumentCommand command, String text) {
		command.text += text;
		command.caretOffset += text.length();
	}
	
	private void addAfterCursor(DocumentCommand command, String text) {
		command.text += text;
	}
	
}
