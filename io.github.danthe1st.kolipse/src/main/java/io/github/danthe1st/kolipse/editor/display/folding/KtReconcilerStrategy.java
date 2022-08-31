package io.github.danthe1st.kolipse.editor.display.folding;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.Position;
import org.eclipse.jface.text.reconciler.DirtyRegion;
import org.eclipse.jface.text.reconciler.IReconcilingStrategy;
import org.eclipse.jface.text.reconciler.IReconcilingStrategyExtension;
import org.eclipse.jface.text.source.Annotation;
import org.eclipse.jface.text.source.projection.ProjectionAnnotation;
import org.eclipse.jface.text.source.projection.ProjectionViewer;
import org.eclipse.ui.statushandlers.StatusManager;

import io.github.danthe1st.kolipse.util.analyzing.KotlinDocumentAnalyzer;
import io.github.danthe1st.kolipse.util.analyzing.KotlinDocumentInfo;

public class KtReconcilerStrategy implements IReconcilingStrategy, IReconcilingStrategyExtension {
	private IDocument document;
	private String oldDocument;
	private ProjectionViewer projectionViewer;
	private List<Annotation> oldAnnotations = new ArrayList<>();
	private List<Position> oldPositions = new ArrayList<>();
	private KotlinDocumentAnalyzer analyzer = new KotlinDocumentAnalyzer();
	
	@Override
	public void setDocument(IDocument document) {
		this.document = document;
	}
	
	public void setProjectionViewer(ProjectionViewer projectionViewer) {
		this.projectionViewer = projectionViewer;
	}
	
	@Override
	public void reconcile(DirtyRegion dirtyRegion, IRegion subRegion) {
		initialReconcile();
	}
	
	@Override
	public void reconcile(IRegion partition) {
		initialReconcile();
	}
	
	@Override
	public void initialReconcile() {
		if(document.get().equals(oldDocument))
			return;
		oldDocument = document.get();
		
		List<Position> positions = getNewPositionsOfAnnotations();
		
		List<Position> positionsToRemove = new ArrayList<>();
		List<Annotation> annotationToRemove = new ArrayList<>();
		
		for(Position position : oldPositions){
			if(!positions.contains(position)){
				projectionViewer.getProjectionAnnotationModel().removeAnnotation(oldAnnotations.get(oldPositions.indexOf(position)));
				positionsToRemove.add(position);
				annotationToRemove.add(oldAnnotations.get(oldPositions.indexOf(position)));
			}else{
				positions.remove(position);
			}
		}
		oldPositions.removeAll(positionsToRemove);
		oldAnnotations.removeAll(annotationToRemove);
		
		for(Position position : positions){
			Annotation annotation = new ProjectionAnnotation();
			projectionViewer.getProjectionAnnotationModel().addAnnotation(annotation, position);
			oldPositions.add(position);
			oldAnnotations.add(annotation);
		}
	}
	
	private List<Position> getNewPositionsOfAnnotations() {
		List<Position> positions = new ArrayList<>();
		try{
			KotlinDocumentInfo info = analyzer.analyzeKotlinDocument(document);
			List<IRegion> normalSourceCodeRegions = info.getNormalSourceCodeRegions();
			Deque<Integer> startPositions = new ArrayDeque<>();
			for(IRegion region : normalSourceCodeRegions){
				String regionText = document.get(region.getOffset(), region.getLength());
				int curIndex = 0;
				int nextIndex;
				while((nextIndex = getNextIndex(regionText.indexOf('{', curIndex), regionText.indexOf('}', curIndex))) != -1){
					char scanChar = regionText.charAt(nextIndex);
					curIndex = nextIndex + 1;
					if(scanChar == '{'){
						startPositions.add(region.getOffset() + nextIndex);
					}else if(scanChar == '}'){
						Integer startPosition = startPositions.poll();
						if(startPosition != null){
							addPosition(positions, document, startPosition, region.getOffset() + nextIndex - startPosition);
						}
					}
				}
			}
			for(IRegion comment : info.getCommentRegions()){
				int start = comment.getOffset();
				int len = comment.getLength();
				addPosition(positions, document, start, len);
			}
		}catch(BadLocationException e){
			StatusManager.getManager().handle(new Status(IStatus.ERROR, getClass(), "An error occured trying to find folding positions", e));
			return Collections.emptyList();
		}
		
		return positions;
	}
	
	private void addPosition(List<Position> positions, IDocument document, int start, int len) throws BadLocationException {
		if(document.getChar(start + len - 1) == '\n'){
			len--;
		}
		if(document.getLineOfOffset(start) != document.getLineOfOffset(start + len)){
			positions.add(new Position(start, len));
		}
	}
	
	private int getNextIndex(int first, int second) {
		if(first == -1){
			return second;
		}
		if(second == -1){
			return first;
		}
		return Math.min(first, second);
	}
	
	@Override
	public void setProgressMonitor(IProgressMonitor monitor) {
		// no progress monitor used
	}
	
}