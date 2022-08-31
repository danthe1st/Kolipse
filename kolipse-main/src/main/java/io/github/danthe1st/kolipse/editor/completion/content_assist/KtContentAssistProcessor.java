package io.github.danthe1st.kolipse.editor.completion.content_assist;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.contentassist.CompletionProposal;
import org.eclipse.jface.text.contentassist.ICompletionProposal;
import org.eclipse.jface.text.contentassist.IContentAssistProcessor;
import org.eclipse.jface.text.contentassist.IContextInformation;
import org.eclipse.jface.text.contentassist.IContextInformationValidator;
import org.eclipse.ui.statushandlers.StatusManager;

import io.github.danthe1st.kolipse.editor.completion.autocomplete.KtAutoEditStrategy;
import io.github.danthe1st.kolipse.util.analyzing.KotlinDocumentAnalyzer;
import io.github.danthe1st.kolipse.util.analyzing.KotlinDocumentInfo;

public class KtContentAssistProcessor implements IContentAssistProcessor {
	
	private KotlinDocumentAnalyzer analyzer = new KotlinDocumentAnalyzer();
	
	private List<Map.Entry<String, ProposalGeneratorFunction>> proposalGenerators = List.of(
			createSimpleProposalGenerator("fun", "fun ", "() {\n\t\n}"),
			createSimpleProposalGenerator("main", "fun main() {\n\t", "\n}"),
			createSimpleProposalGenerator("if", "if (", ") {\n\t\n}"),
			createSimpleProposalGenerator("while", "while (", ") {\n\t\n}"),
			createSimpleProposalGenerator("class", "class ", " {\n\t\n}"),
			createSimpleProposalGenerator("object", "object ", " {\n\t\n}"),
			createSimpleProposalGenerator("data class", "data class ", "() {\n\t\n}"),
			createSimpleProposalGenerator("interface", "interface ", " {\n\t\n}"),
			createSimpleProposalGenerator("annotation", "annotation ", " {\n\t\n}")
	);
	
	@Override
	public ICompletionProposal[] computeCompletionProposals(ITextViewer viewer, int offset) {
		String text = viewer.getDocument().get();
		int lastNonWhitespaceCharacterIndex = offset - 1;
		while(lastNonWhitespaceCharacterIndex > 0 && !Character.isWhitespace(text.charAt(lastNonWhitespaceCharacterIndex))){
			lastNonWhitespaceCharacterIndex--;
		}
		String lastWord = text.substring(lastNonWhitespaceCharacterIndex + 1, offset);
		return Stream.concat(
				proposalGenerators.stream().filter(e -> e.getKey().startsWith(lastWord))
					.map(e -> e.getValue().createProposal(viewer, offset - lastWord.length(), lastWord.length())),
				getTypeProposals(viewer, offset - lastWord.length(), lastWord).stream()
		)
			
			.toArray(ICompletionProposal[]::new);
	}
	
	private List<ICompletionProposal> getTypeProposals(ITextViewer viewer, int offset, String start) {
		List<ICompletionProposal> proposals = new ArrayList<>();
		KotlinDocumentInfo info;
		try{
			info = analyzer.analyzeKotlinDocument(viewer.getDocument());
		}catch(BadLocationException e){
			StatusManager.getManager().handle(new Status(IStatus.ERROR, getClass(), "An exception occured trying to analyze the Kotlin file in order to get Kotlin Type proposals", e));
			return Collections.emptyList();
		}
		for(String importShortName : info.getImportShortNames()){
			if(importShortName.startsWith(start)){
				proposals.add(new CompletionProposal(importShortName, offset, start.length(), importShortName.length()));
			}
		}
		// TODO better/more type proposals
		return proposals;
	}
	
	private Map.Entry<String, ProposalGeneratorFunction> createSimpleProposalGenerator(String displayString, String start, String end) {
		return Map.entry(displayString.replace(" ", "_"), (viewer, offset, numberOfCharactersToReplace) -> {
			String actualStart = start;
			String actualEnd = end;
			try{
				IRegion lineInformation = viewer.getDocument().getLineInformationOfOffset(offset);
				String line = viewer.getDocument().get(lineInformation.getOffset(), lineInformation.getLength());
				String trailingWhitespace = KtAutoEditStrategy.getTrailingWhitespace(line);
				actualStart = actualStart.replace("\n", "\n" + trailingWhitespace);
				actualEnd = actualEnd.replace("\n", "\n" + trailingWhitespace);
			}catch(BadLocationException e){
				StatusManager.getManager().handle(new Status(IStatus.ERROR, getClass(), "An exception occured trying to create a (template) proposal", e));
			}
			
			return new CompletionProposal(actualStart + actualEnd, offset, numberOfCharactersToReplace, actualStart.length(), null, displayString, null, null);
		});
	}
	
	@Override
	public IContextInformation[] computeContextInformation(ITextViewer viewer, int offset) {
		return new IContextInformation[0];
	}
	
	@Override
	public char[] getCompletionProposalAutoActivationCharacters() {
		return new char[] { '\n', ' ' };
	}
	
	@Override
	public char[] getContextInformationAutoActivationCharacters() {
		return new char[0];
	}
	
	@Override
	public String getErrorMessage() {
		return "No proposals found";
	}
	
	@Override
	public IContextInformationValidator getContextInformationValidator() {
		return null;
	}
	
}