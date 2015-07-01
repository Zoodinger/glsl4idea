package glslplugin;

import com.intellij.ide.fileTemplates.DefaultCreateFromTemplateHandler;
import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.util.IncorrectOperationException;
import glslplugin.lang.GLSLFileType;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Handles creating new shader file templates.
 *
 * It can change the template text based on the given extension.
 *
 * @author Jan Polák
 */
public class GLSLCreateFromTemplateHandler extends DefaultCreateFromTemplateHandler {

    public static final String DEFAULT_EXTENSION = "glsl";

    private static void addTemplates(Map<String, FileTemplate> result, FileTemplate[] templates){
        for(FileTemplate template:templates){
            if(template.isTemplateOfType(GLSLSupportLoader.GLSL) && GLSLFileType.EXTENSIONS.contains(template.getExtension())){
                FileTemplate existing = result.get(template.getExtension());
                if(existing == null){
                    //Do not replace existing templates to keep well defined priority order
                    result.put(template.getExtension(), template);
                }
            }
        }
    }

    /**
     * Return map of extensions and their preferred template.
     * This map is obtained from taking all internal templates and overriding them with existing custom templates.
     */
    private static Map<String, FileTemplate> getTemplates(Project project){
        final Map<String, FileTemplate> result = new HashMap<String, FileTemplate>();
        FileTemplateManager manager = FileTemplateManager.getInstance(project);
        addTemplates(result,manager.getInternalTemplates());
        addTemplates(result,manager.getAllTemplates());
        return result;
    }

    @Override
    public boolean handlesTemplate(FileTemplate template) {
        return template.isTemplateOfType(GLSLSupportLoader.GLSL);
    }

    //Copied and modified from parent class
    @NotNull
    @Override
    public PsiFile createFromTemplate(final Project project, final PsiDirectory directory, String fileName, FileTemplate template,
                                         String templateText,
                                         @NotNull final Map<String, Object> props) throws IncorrectOperationException {
        Map<String, FileTemplate> templates = getTemplates(project);
        //Make sure it has some extension
        int extensionDot = fileName.lastIndexOf('.');
        String extension;
        if(extensionDot == -1){
            extension = template.getExtension();
            fileName = fileName+"."+extension;
        }else{
            extension = fileName.substring(extensionDot+1);
            if(extension.isEmpty()){
                //Filename ends with a dot
                extension = template.getExtension();
                fileName = fileName + extension;
            } else {
                //Do template replacement
                FileTemplate alternateTemplate = templates.get(extension.toLowerCase());
                if(alternateTemplate != null){
                    //There is a template defined for this
                    try {
                        templateText = alternateTemplate.getText(props);
                    } catch (IOException e) {
                        throw new IncorrectOperationException("Failed to load template file.", (Throwable)e);
                    }
                }
            }
        }
        //Make sure that the extension is valid
        if(!templates.containsKey(extension.toLowerCase())){
            //Extension is not recognized, add recognized default one
            fileName = fileName + "." + DEFAULT_EXTENSION;
        }

        if (FileTypeManager.getInstance().isFileIgnored(fileName)) {
            throw new IncorrectOperationException("This filename is ignored (Settings | File Types | Ignore files and folders)");
        }

        directory.checkCreateFile(fileName);
        FileType type = FileTypeRegistry.getInstance().getFileTypeByFileName(fileName);
        PsiFile file = PsiFileFactory.getInstance(project).createFileFromText(fileName, type, templateText);

        if (template.isReformatCode()) {
            CodeStyleManager.getInstance(project).reformat(file);
        }

        file = (PsiFile)directory.add(file);
        return file;
    }
}
