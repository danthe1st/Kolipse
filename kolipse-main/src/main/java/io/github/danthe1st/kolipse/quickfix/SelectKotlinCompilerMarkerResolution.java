package io.github.danthe1st.kolipse.quickfix;

import java.nio.file.Files;
import java.nio.file.Path;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.swt.widgets.DirectoryDialog;
import org.eclipse.ui.PlatformUI;

class SelectKotlinCompilerMarkerResolution extends AbstractKotlinCompilerMarkerResolution {
	
	@Override
	public String getDescription() {
		return "Choose a Kotlin compiler executable";
	}
	
	@Override
	public String getLabel() {
		return "Select Kotlin compiler";
	}
	
	@Override
	protected Path getCompilerPath(IProgressMonitor monitor) throws Exception {
		DirectoryDialog dlg = new DirectoryDialog(PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell());
		Path path = Path.of(dlg.open());
		if(existsAll(path, "bin/kotlinc", "lib/kotlin-stdlib.jar", "lib/kotlin-compiler.jar")){
			return path;
		}
		return null;
	}
	
	private boolean existsAll(Path path, String... toResolve) {
		for(String rel : toResolve){
			if(!Files.exists(path.resolve(rel))){
				return false;
			}
		}
		return true;
	}
	
}