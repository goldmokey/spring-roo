package org.springframework.roo.classpath.converters;

import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.springframework.roo.classpath.TypeLocationService;
import org.springframework.roo.model.JavaPackage;
import org.springframework.roo.model.JavaType;
import org.springframework.roo.project.ProjectOperations;
import org.springframework.roo.project.maven.Pom;
import org.springframework.roo.shell.Shell;
import org.springframework.roo.support.util.AnsiEscapeCode;
import org.springframework.roo.support.util.Assert;
import org.springframework.roo.support.util.StringUtils;

/**
 * Records the last Java package and type used.
 *
 * @author Ben Alex
 * @since 1.0
 */
@Component
@Service
public class LastUsedImpl implements LastUsed {

	// Fields
	@Reference private Shell shell;
	@Reference private TypeLocationService typeLocationService;
	@Reference private ProjectOperations projectOperations;

	private JavaPackage javaPackage;
	private JavaType javaType;
	private JavaPackage topLevelPackage;
	private Pom module;

	public void setPackage(final JavaPackage javaPackage) {
		Assert.notNull(javaPackage, "JavaPackage required");
		if (javaPackage.getFullyQualifiedPackageName().startsWith("java.")) {
			return;
		}
		this.javaType = null;
		this.javaPackage = javaPackage;
		setPromptPath(javaPackage.getFullyQualifiedPackageName());
	}

	public void setType(final JavaType javaType) {
		Assert.notNull(javaType, "JavaType required");
		if (javaType.getPackage().getFullyQualifiedPackageName().startsWith("java.")) {
			return;
		}
		this.javaType = javaType;
		this.javaPackage = javaType.getPackage();
		setPromptPath(javaType.getFullyQualifiedTypeName());
	}

	public void setType(JavaType javaType, Pom module) {
		Assert.notNull(javaType, "JavaType required");
		if (javaType.getPackage().getFullyQualifiedPackageName().startsWith("java.")) {
			return;
		}
		this.module = module;
		this.javaType = javaType;
		this.javaPackage = javaType.getPackage();
		setPromptPath(javaType.getFullyQualifiedTypeName());
	}

	private void setPromptPath(String fullyQualifiedName) {
		if (topLevelPackage == null) {
			return;
		}

		topLevelPackage =  new JavaPackage(typeLocationService.getTopLevelPackageForModule(projectOperations.getFocusedModule()));
		System.out.println("topLevePackage: " + topLevelPackage);

		String moduleName = "";
		if (module != null) {

			if (StringUtils.hasText(module.getModuleName())) {
				moduleName = AnsiEscapeCode.decorate(module.getModuleName() + "|", AnsiEscapeCode.FG_CYAN);
			}
		}

		String path = moduleName + fullyQualifiedName.replace(topLevelPackage.getFullyQualifiedPackageName(), "~");
		shell.setPromptPath(path, StringUtils.hasText(moduleName));

	}

	public JavaPackage getTopLevelPackage() {
		return topLevelPackage;
	}

	public void setTopLevelPackage(final JavaPackage topLevelPackage) {
		this.topLevelPackage = topLevelPackage;
	}

	public JavaType getJavaType() {
		return javaType;
	}

	public JavaPackage getJavaPackage() {
		return javaPackage;
	}
}
