import com.android.build.gradle.AppExtension;

import org.gradle.api.Plugin;
import org.gradle.api.Project;

public class JunkCodePlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        AppExtension appExtension = project.getExtensions().findByType(AppExtension.class);
        if(appExtension != null) {
            appExtension.registerTransform(new JunkCodeTransform(project));
        }
    }
}

