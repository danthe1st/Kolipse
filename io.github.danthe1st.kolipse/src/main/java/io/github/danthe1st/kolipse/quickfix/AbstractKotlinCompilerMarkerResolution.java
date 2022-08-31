package io.github.danthe1st.kolipse.quickfix;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.stream.Collectors;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.DocumentEvent;
import org.eclipse.swt.graphics.Image;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.views.markers.WorkbenchMarkerResolution;
import org.osgi.service.prefs.BackingStoreException;

import io.github.danthe1st.kolipse.editor.display.validation.ValidatorDocumentSetupParticipant;
import io.github.danthe1st.kolipse.editor.display.validation.ValidatorDocumentSetupParticipant.DocumentValidator;
import io.github.danthe1st.kolipse.nature.KotlinProjectNature;

abstract class AbstractKotlinCompilerMarkerResolution extends WorkbenchMarkerResolution {
	// TODO allow changing Kotlin compiler
	@Override
	public void run(IMarker[] markers, IProgressMonitor monitor) {
		Path compilerPath;
		try{
			compilerPath = getCompilerPath(IProgressMonitor.nullSafe(monitor));
			if(compilerPath == null){
				Dialog dlg = new MessageDialog(PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell(), "title", null, "msg", MessageDialog.ERROR, 0, "Ok");
				dlg.open();
			}
			Arrays
				.stream(markers)
				.map(m -> m.getResource().getProject())
				.filter(p -> {
					try{
						return p.hasNature(KotlinProjectNature.NATURE_ID);
					}catch(CoreException e){
						throw new RuntimeException(e);
					}
				})
				.distinct()
				.forEach(project -> {
					try{
						KotlinProjectNature nature = (KotlinProjectNature) project.getNature(KotlinProjectNature.NATURE_ID);
						nature.setKotlinCompilerPath(compilerPath);
						project.build(IncrementalProjectBuilder.CLEAN_BUILD, IProgressMonitor.nullSafe(monitor));
					}catch(CoreException e){
						throw new RuntimeException(e);
					}catch(BackingStoreException e){
						throw new RuntimeException(e);
					}
				});
			for(IMarker marker : markers){
				IResource resource = marker.getResource();
				// TODO also trigger project recompilation
				if(resource instanceof IFile){
					IFile file = (IFile) resource;
					try(BufferedReader br = new BufferedReader(new InputStreamReader(file.getContents(), file.getCharset()))){
						String contents = br.lines().collect(Collectors.joining("\n"));
						DocumentValidator validator = new ValidatorDocumentSetupParticipant.DocumentValidator(file);
						validator.documentChanged(new DocumentEvent(new Document(contents), 0, 0, ""));
					}
					
				}
			}
		}catch(Exception e1){
			throw new RuntimeException(e1);
		}
		
	}
	
	protected abstract Path getCompilerPath(IProgressMonitor monitor) throws Exception;
	
	@Override
	public void run(IMarker marker) {
		run(new IMarker[] { marker }, null);
	}
	
	@Override
	public Image getImage() {
		return null;
	}
	
	@Override
	public IMarker[] findOtherMarkers(IMarker[] markers) {
		return Arrays
			.stream(markers)
			.filter(marker -> {
				try{
					return "KOTLIN_COMPILER_MISSING".equals(marker.getAttribute("kolipse_type"));
				}catch(CoreException e1){
					return false;
				}
			})
			.filter(marker -> {
				try{
					return marker.getResource().getProject().hasNature(KotlinProjectNature.NATURE_ID);
				}catch(CoreException e){
					return false;
				}
			})
			.toArray(IMarker[]::new);
	}
}
