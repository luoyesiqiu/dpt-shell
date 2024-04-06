import com.android.build.api.transform.DirectoryInput;
import com.android.build.api.transform.Format;
import com.android.build.api.transform.JarInput;
import com.android.build.api.transform.QualifiedContent;
import com.android.build.api.transform.Transform;
import com.android.build.api.transform.TransformException;
import com.android.build.api.transform.TransformInput;
import com.android.build.api.transform.TransformInvocation;
import com.android.build.api.transform.TransformOutputProvider;
import com.android.build.gradle.internal.pipeline.TransformManager;
import com.luoye.dpt.plugin.asm.JunkCodeGenerator;
import com.luoye.dpt.plugin.asm.util.LogUtils;

import org.apache.commons.io.FileUtils;
import org.gradle.api.Project;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Set;

public class JunkCodeTransform extends Transform {
    private final Project project;
    public JunkCodeTransform(Project project) {
        this.project = project;
    }
    @Override
    public String getName() {
        return JunkCodeTransform.class.getName();
    }

    @Override
    public Set<QualifiedContent.ContentType> getInputTypes() {
        return TransformManager.CONTENT_CLASS;
    }

    @Override
    public Set<? super QualifiedContent.Scope> getScopes() {
        return TransformManager.SCOPE_FULL_PROJECT;
    }

    @Override
    public boolean isIncremental() {
        return false;
    }

    @Override
    public void transform(TransformInvocation transformInvocation) throws TransformException, InterruptedException, IOException {
        super.transform(transformInvocation);
        Collection<TransformInput> inputs = transformInvocation.getInputs();
        for (TransformInput input : inputs) {

            Collection<JarInput> jarInputs = input.getJarInputs();
            for (JarInput jarInput : jarInputs) {
                LogUtils.debug("jarInput %s", jarInput.getFile().toString());
                processJarInput(jarInput,transformInvocation.getOutputProvider());
            }

            Collection<DirectoryInput> directoryInputs = input.getDirectoryInputs();
            for (DirectoryInput directoryInput : directoryInputs) {
                LogUtils.debug("directoryInput %s", directoryInput.getFile().toString());
                processDirectoryInputs(directoryInput,transformInvocation.getOutputProvider());
            }
        }
    }

    private static void processJarInput(JarInput jarInput, TransformOutputProvider outputProvider)
            throws IOException {
        File dest = outputProvider.getContentLocation(
                jarInput.getFile().getAbsolutePath(),
                jarInput.getContentTypes(),
                jarInput.getScopes(),
                Format.JAR);

        LogUtils.debug("processJarInput dest: %s", dest);

        FileUtils.copyFile(jarInput.getFile(), dest);
    }

    private static void processDirectoryInputs(DirectoryInput directoryInput, TransformOutputProvider outputProvider)
            throws IOException {

        File dest = outputProvider.getContentLocation(directoryInput.getName(),
                directoryInput.getContentTypes(), directoryInput.getScopes(),
                Format.DIRECTORY);
        FileUtils.forceMkdir(dest);

        LogUtils.debug("processDirectoryInputs dest: %s", dest);

        JunkCodeGenerator.generate(directoryInput.getFile());

        FileUtils.copyDirectory(directoryInput.getFile(), dest);
    }


}
