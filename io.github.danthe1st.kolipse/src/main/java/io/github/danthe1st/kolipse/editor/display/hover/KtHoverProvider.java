package io.github.danthe1st.kolipse.editor.display.hover;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.core.filebuffers.FileBuffers;
import org.eclipse.core.filebuffers.ITextFileBuffer;
import org.eclipse.core.filebuffers.ITextFileBufferManager;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.ISourceRange;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.DefaultInformationControl;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IInformationControlCreator;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.ITextHover;
import org.eclipse.jface.text.ITextHoverExtension;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.Region;
import org.eclipse.ui.editors.text.EditorsUI;
import org.eclipse.ui.statushandlers.StatusManager;

import io.github.danthe1st.kolipse.nature.KotlinProjectNature;
import io.github.danthe1st.kolipse.util.analyzing.KotlinDocumentAnalyzer;
import io.github.danthe1st.kolipse.util.analyzing.KotlinDocumentInfo;

public class KtHoverProvider implements ITextHover, ITextHoverExtension {
	private KotlinDocumentAnalyzer analyzer = new KotlinDocumentAnalyzer();
	
	@Override
	public String getHoverInfo(ITextViewer textViewer, IRegion hoverRegion) {
		try{
			KotlinDocumentInfo info = analyzer.analyzeKotlinDocument(textViewer.getDocument());
			if(info.isInComment(hoverRegion.getOffset())){
				return null;
			}
			String word = textViewer.getDocument().get(hoverRegion.getOffset(), hoverRegion.getLength());
			ITextFileBufferManager bufferManager = FileBuffers.getTextFileBufferManager();
			ITextFileBuffer textFileBuffer = bufferManager.getTextFileBuffer(textViewer.getDocument());
			IProject project = ResourcesPlugin.getWorkspace().getRoot().getFile(textFileBuffer.getLocation()).getProject();
			if(project.isNatureEnabled(KotlinProjectNature.NATURE_ID)){
				IJavaProject javaProject = JavaCore.create(project);
				if(javaProject == null){
					throw new RuntimeException("Project has Kotlin nature but is not a Java project");
				}
				IType type = tryFindType(info, word, javaProject);
				
				if(type != null){
					String javadoc = type.getAttachedJavadoc(null);
					if(javadoc == null){
						ISourceRange javadocRange = type.getJavadocRange();
						IResource resource = type.getResource();
						if(javadocRange == null || !(resource instanceof IFile)){
							javadoc = "";
						}else{
							IFile resourceFile = (IFile) resource;
							try(BufferedReader br = new BufferedReader(new InputStreamReader(resourceFile.getContents(), resourceFile.getCharset()))){
								br.skip(javadocRange.getOffset());
								char[] chars = new char[javadocRange.getLength()];
								br.read(chars);
								javadoc = new String(chars).replace("\n", "<br/>");
							}catch(IOException e){
								StatusManager.getManager().handle(new Status(IStatus.ERROR, getClass(), "An exception occured trying to get the Javadoc of " + resource, e));
								javadoc = "";
							}
						}
					}
					String superclassName = type.getSuperclassName();
					String superTypeString = Stream.concat(
							Stream.of(superclassName),
							Arrays.stream(type.getSuperInterfaceNames())
					)
						.filter(Objects::nonNull)
						.collect(Collectors.joining(", "));
					if(!superTypeString.isEmpty()){
						superTypeString = ": " + superTypeString;
					}
					String packageString = type.getPackageFragment().getElementName();
					if(!packageString.isEmpty()){
						packageString = packageString + ".";
					}
					return "<p>" + packageString + "<b>" + type.getElementName() + "</b>" + superTypeString + "</p><br/><div>" + javadoc + "</div>";
				}
				
				return null;
			}
		}catch(BadLocationException e){
			StatusManager.getManager().handle(new Status(IStatus.ERROR, getClass(), "An exception occured trying to get hover information", e));
		}catch(CoreException e){
			throw new RuntimeException(e);
		}
		return null;
	}
	
	private IType tryFindType(KotlinDocumentInfo info, String word, IJavaProject javaProject) throws JavaModelException {
		String importedWord = info.getImport(word);// TODO methods/fields/...
		List<String> attempts = new ArrayList<>();
		if(importedWord == null){
			attempts.add(word);
			if(!word.contains(".")){
				attempts.add("kotlin." + word);// TODO general wildcard imports
			}
		}else{
			attempts.add(importedWord);
		}
		for(String attempt : attempts){
			IType type = javaProject.findType(attempt);
			if(type != null){
				return type;
			}
		}
		if(word.contains(".")){
			return tryFindType(info, word.substring(0, word.lastIndexOf(".")), javaProject);
			// TODO show method/field/... in that case
		}
		return null;
	}
	
	@Override
	public IRegion getHoverRegion(ITextViewer textViewer, int offset) {
		IDocument document = textViewer.getDocument();
		int wordStartIndex = offset;
		int wordEndIndex = offset;
		try{
			while(wordStartIndex > 0 && isWordChar(document.getChar(wordStartIndex))){
				wordStartIndex--;
			}
			if(wordStartIndex != offset){
				wordStartIndex++;
			}
			while(wordEndIndex < document.getLength() && Character.isJavaIdentifierPart(document.getChar(wordEndIndex))){
				wordEndIndex++;
			}
			return new Region(wordStartIndex, wordEndIndex - wordStartIndex);
		}catch(BadLocationException e){
			StatusManager.getManager().handle(new Status(IStatus.ERROR, getClass(), "An exception occured trying to get hover information", e));
			return new Region(offset, 0);
		}
	}
	
	private boolean isWordChar(char c) {
		return Character.isJavaIdentifierPart(c) || c == '.';
	}
	
	@Override
	public IInformationControlCreator getHoverControlCreator() {
		return parent -> new DefaultInformationControl(parent, EditorsUI.getTooltipAffordanceString());
	}
}