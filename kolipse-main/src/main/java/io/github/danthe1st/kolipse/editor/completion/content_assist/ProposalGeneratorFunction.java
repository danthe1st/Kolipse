package io.github.danthe1st.kolipse.editor.completion.content_assist;

import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.contentassist.ICompletionProposal;

@FunctionalInterface
public interface ProposalGeneratorFunction {
	ICompletionProposal createProposal(ITextViewer viewer, int offset, int numberOfCharactersToReplace);
}
