package io.github.danthe1st.kolipse.editor.display.folding;

import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.reconciler.Reconciler;
import org.eclipse.jface.text.source.projection.ProjectionViewer;

public class KtReconciler extends Reconciler {
	
	private KtReconcilerStrategy fStrategy;
	
	public KtReconciler() {
		fStrategy = new KtReconcilerStrategy();
		this.setReconcilingStrategy(fStrategy, IDocument.DEFAULT_CONTENT_TYPE);
	}
	
	@Override
	public void install(ITextViewer textViewer) {
		super.install(textViewer);
		ProjectionViewer pViewer = (ProjectionViewer) textViewer;
		fStrategy.setProjectionViewer(pViewer);
	}
}