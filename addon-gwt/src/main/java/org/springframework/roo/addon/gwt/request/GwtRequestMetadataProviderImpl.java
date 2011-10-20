package org.springframework.roo.addon.gwt.request;

import static org.springframework.roo.model.RooJavaType.ROO_GWT_PROXY;
import static org.springframework.roo.model.RooJavaType.ROO_GWT_REQUEST;
import static org.springframework.roo.model.RooJavaType.ROO_SERVICE;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.osgi.service.component.ComponentContext;
import org.springframework.roo.addon.gwt.GwtFileManager;
import org.springframework.roo.addon.gwt.GwtTypeService;
import org.springframework.roo.addon.gwt.GwtUtils;
import org.springframework.roo.classpath.PhysicalTypeIdentifier;
import org.springframework.roo.classpath.PhysicalTypeIdentifierNamingUtils;
import org.springframework.roo.classpath.TypeLocationService;
import org.springframework.roo.classpath.details.ClassOrInterfaceTypeDetails;
import org.springframework.roo.classpath.details.ClassOrInterfaceTypeDetailsBuilder;
import org.springframework.roo.classpath.details.MemberFindingUtils;
import org.springframework.roo.classpath.details.MethodMetadata;
import org.springframework.roo.classpath.details.MethodMetadataBuilder;
import org.springframework.roo.classpath.details.annotations.AnnotatedJavaType;
import org.springframework.roo.classpath.details.annotations.AnnotationAttributeValue;
import org.springframework.roo.classpath.details.annotations.AnnotationMetadata;
import org.springframework.roo.classpath.details.annotations.AnnotationMetadataBuilder;
import org.springframework.roo.classpath.details.annotations.ClassAttributeValue;
import org.springframework.roo.classpath.itd.InvocableMemberBodyBuilder;
import org.springframework.roo.classpath.scanner.MemberDetails;
import org.springframework.roo.classpath.scanner.MemberDetailsScanner;
import org.springframework.roo.metadata.AbstractHashCodeTrackingMetadataNotifier;
import org.springframework.roo.metadata.MetadataIdentificationUtils;
import org.springframework.roo.metadata.MetadataItem;
import org.springframework.roo.model.DataType;
import org.springframework.roo.model.JavaType;
import org.springframework.roo.project.ContextualPath;
import org.springframework.roo.project.ProjectMetadata;
import org.springframework.roo.project.ProjectOperations;
import org.springframework.roo.support.util.Assert;
import org.springframework.roo.support.util.StringUtils;

@Component(immediate = true)
@Service
public class GwtRequestMetadataProviderImpl extends AbstractHashCodeTrackingMetadataNotifier implements GwtRequestMetadataProvider {

	// Fields
	@Reference protected GwtTypeService gwtTypeService;
	@Reference protected MemberDetailsScanner memberDetailsScanner;
	@Reference protected ProjectOperations projectOperations;
	@Reference protected TypeLocationService typeLocationService;
	@Reference protected GwtFileManager gwtFileManager;

	protected void activate(final ComponentContext context) {
		metadataDependencyRegistry.registerDependency(PhysicalTypeIdentifier.getMetadataIdentiferType(), getProvidesType());
	}

	protected void deactivate(final ComponentContext context) {
		metadataDependencyRegistry.deregisterDependency(PhysicalTypeIdentifier.getMetadataIdentiferType(), getProvidesType());
	}

	public String getProvidesType() {
		return GwtRequestMetadata.getMetadataIdentifierType();
	}

	public MetadataItem get(final String metadataIdentificationString) {
		// Abort early if we can't continue
		ProjectMetadata projectMetadata = projectOperations.getProjectMetadata(PhysicalTypeIdentifierNamingUtils.getPath(metadataIdentificationString).getModule());
		if (projectMetadata == null) {
			return null;
		}

		ClassOrInterfaceTypeDetails request = getGovernor(metadataIdentificationString);
		if (request == null) {
			return null;
		}

		AnnotationMetadata mirrorAnnotation = MemberFindingUtils.getAnnotationOfType(request.getAnnotations(), ROO_GWT_REQUEST);
		if (mirrorAnnotation == null) {
			return null;
		}

		JavaType targetType = GwtUtils.lookupRequestTargetType(request);
		if (targetType == null) {
			return null;
		}

		ClassOrInterfaceTypeDetails target = typeLocationService.getTypeDetails(targetType);
		if (target == null || Modifier.isAbstract(target.getModifier())) {
			return null;
		}

		MemberDetails memberDetails = memberDetailsScanner.getMemberDetails(getClass().getName(), target);
		if (memberDetails == null) {
			return null;
		}

		List<String> exclusionsList = getMethodExclusions(request);

		List<MethodMetadata> requestMethods = new ArrayList<MethodMetadata>();
		for (MethodMetadata methodMetadata : memberDetails.getMethods()) {
			if (Modifier.isPublic(methodMetadata.getModifier()) && !exclusionsList.contains(methodMetadata.getMethodName().getSymbolName())) {
				JavaType returnType = gwtTypeService.getGwtSideLeafType(methodMetadata.getReturnType(), target.getName(), true, true);
				if (returnType == null) {
					continue;
				}
				MethodMetadataBuilder methodBuilder = new MethodMetadataBuilder(methodMetadata);
				methodBuilder.setReturnType(returnType);
				methodBuilder.setBodyBuilder(null);
				requestMethods.add(methodBuilder.build());
			}
		}
		GwtRequestMetadata gwtRequestMetadata = new GwtRequestMetadata(metadataIdentificationString, updateRequest(request, requestMethods));
		notifyIfRequired(gwtRequestMetadata);
		return gwtRequestMetadata;
	}

	private List<String> getMethodExclusions(final ClassOrInterfaceTypeDetails request) {
		List<String> exclusionList = GwtUtils.getAnnotationValues(request, ROO_GWT_REQUEST, "exclude");
		ClassOrInterfaceTypeDetails proxy = gwtTypeService.lookupProxyFromRequest(request);
		if (proxy != null) {
			Boolean ignoreProxyExclusions = GwtUtils.getBooleanAnnotationValue(request, ROO_GWT_REQUEST, "ignoreProxyExclusions", false);
			if (!ignoreProxyExclusions) {
				for (String exclusion : GwtUtils.getAnnotationValues(proxy, ROO_GWT_PROXY, "exclude")) {
					exclusionList.add("set" + StringUtils.capitalize(exclusion));
					exclusionList.add("get" + StringUtils.capitalize(exclusion));
				}
				exclusionList.addAll(GwtUtils.getAnnotationValues(proxy, ROO_GWT_PROXY, "exclude"));
			}
			Boolean ignoreProxyReadOnly = GwtUtils.getBooleanAnnotationValue(request, ROO_GWT_REQUEST, "ignoreProxyReadOnly", false);
			if (!ignoreProxyReadOnly) {
				for (String exclusion : GwtUtils.getAnnotationValues(proxy, ROO_GWT_PROXY, "readOnly")) {
					exclusionList.add("set" + StringUtils.capitalize(exclusion));
				}
			}
			Boolean dontIncludeProxyMethods = GwtUtils.getBooleanAnnotationValue(proxy, ROO_GWT_REQUEST, "ignoreProxyReadOnly", true);
			if (dontIncludeProxyMethods) {
				for (MethodMetadata methodMetadata : proxy.getDeclaredMethods())  {
					exclusionList.add(methodMetadata.getMethodName().getSymbolName());
				}
			}
		}
		return exclusionList;
	}

	private ClassOrInterfaceTypeDetails getGovernor(final String metadataIdentificationString) {
		JavaType governorTypeName = GwtRequestMetadata.getJavaType(metadataIdentificationString);
		ContextualPath governorTypePath = GwtRequestMetadata.getPath(metadataIdentificationString);
		String physicalTypeId = PhysicalTypeIdentifier.createIdentifier(governorTypeName, governorTypePath);
		return typeLocationService.getTypeDetails(physicalTypeId);
	}

	public String updateRequest(final ClassOrInterfaceTypeDetails request, final List<MethodMetadata> requestMethods) {
		List<MethodMetadataBuilder> methods = new ArrayList<MethodMetadataBuilder>();
		for (MethodMetadata method : requestMethods) {
			methods.add(getRequestMethod(request, method));
		}

		ClassOrInterfaceTypeDetailsBuilder typeDetailsBuilder = new ClassOrInterfaceTypeDetailsBuilder(request);

		// Only inherit from RequestContext if extension is not already defined
		if (!typeDetailsBuilder.getExtendsTypes().contains(GwtUtils.OLD_REQUEST_CONTEXT) && !typeDetailsBuilder.getExtendsTypes().contains(GwtUtils.REQUEST_CONTEXT)) {
			typeDetailsBuilder.addExtendsTypes(GwtUtils.REQUEST_CONTEXT);
		}

		if (!typeDetailsBuilder.getExtendsTypes().contains(GwtUtils.REQUEST_CONTEXT)) {
			typeDetailsBuilder.addExtendsTypes(GwtUtils.REQUEST_CONTEXT);
		}

		ClassOrInterfaceTypeDetails entity = gwtTypeService.lookupEntityFromRequest(request);
		AnnotationMetadata annotationMetadata = GwtUtils.getFirstAnnotation(request, GwtUtils.REQUEST_ANNOTATIONS);
		if (annotationMetadata != null && entity != null) {
			AnnotationMetadataBuilder annotationMetadataBuilder = new AnnotationMetadataBuilder(annotationMetadata);
			annotationMetadataBuilder.addStringAttribute("value", entity.getName().getFullyQualifiedTypeName());
			annotationMetadataBuilder.removeAttribute("locator");
			Set<ClassOrInterfaceTypeDetails> services = typeLocationService.findClassesOrInterfaceDetailsWithAnnotation(ROO_SERVICE);
			for (ClassOrInterfaceTypeDetails serviceLayer : services) {
				AnnotationMetadata serviceAnnotation = serviceLayer.getTypeAnnotation(ROO_SERVICE);
				AnnotationAttributeValue<List<ClassAttributeValue>> domainTypesAnnotation = serviceAnnotation.getAttribute("domainTypes");
				for (ClassAttributeValue classAttributeValue : domainTypesAnnotation.getValue()) {
					if (classAttributeValue.getValue().equals(entity.getName())) {
						annotationMetadataBuilder.addStringAttribute("value", serviceLayer.getName().getFullyQualifiedTypeName());
						ContextualPath path = PhysicalTypeIdentifier.getPath(request.getDeclaredByMetadataId());
						annotationMetadataBuilder.addStringAttribute("locator", projectOperations.getTopLevelPackage(path.getModule()) + ".server.locator.GwtServiceLocator");
					}
				}
			}
			typeDetailsBuilder.removeAnnotation(annotationMetadata.getAnnotationType());
			typeDetailsBuilder.updateTypeAnnotation(annotationMetadataBuilder);
		}
		typeDetailsBuilder.setDeclaredMethods(methods);
		return gwtFileManager.write(typeDetailsBuilder.build(), GwtUtils.PROXY_REQUEST_WARNING);
	}

	private MethodMetadataBuilder getRequestMethod(final ClassOrInterfaceTypeDetails request, final MethodMetadata methodMetadata) {
		ClassOrInterfaceTypeDetails proxy = gwtTypeService.lookupProxyFromRequest(request);
		ClassOrInterfaceTypeDetails service = gwtTypeService.lookupTargetServiceFromRequest(request);
		if (proxy == null || service == null) {
			return null;
		}
		ClassOrInterfaceTypeDetails entity = gwtTypeService.lookupEntityFromProxy(proxy);
		if (entity == null) {
			return null;
		}

		List<JavaType> methodReturnTypeArgs;
		JavaType methodReturnType;
		if (entity.getName().equals(service.getName()) && !Modifier.isStatic(methodMetadata.getModifier())) {
			methodReturnTypeArgs = Arrays.asList(proxy.getName(), methodMetadata.getReturnType());
			methodReturnType = new JavaType(GwtUtils.INSTANCE_REQUEST.getFullyQualifiedTypeName(), 0, DataType.TYPE, null, methodReturnTypeArgs);
		} else {
			methodReturnTypeArgs = Collections.singletonList(methodMetadata.getReturnType());
			methodReturnType = new JavaType(GwtUtils.REQUEST.getFullyQualifiedTypeName(), 0, DataType.TYPE, null, methodReturnTypeArgs);
		}
		return getRequestMethod(request, methodMetadata, methodReturnType);
	}

	private MethodMetadataBuilder getRequestMethod(final ClassOrInterfaceTypeDetails request, final MethodMetadata methodMetadata, final JavaType methodReturnType) {
		List<AnnotatedJavaType> paramaterTypes = new ArrayList<AnnotatedJavaType>();
		ClassOrInterfaceTypeDetails mirroredTypeDetails = gwtTypeService.lookupEntityFromRequest(request);
		if (mirroredTypeDetails == null) {
			return null;
		}
		ContextualPath path = PhysicalTypeIdentifier.getPath(request.getDeclaredByMetadataId());
		ProjectMetadata projectMetadata = projectOperations.getProjectMetadata(path.getModule());
		for (AnnotatedJavaType parameterType : methodMetadata.getParameterTypes()) {
			paramaterTypes.add(new AnnotatedJavaType(gwtTypeService.getGwtSideLeafType(parameterType.getJavaType(), mirroredTypeDetails.getName(), true, false)));
		}
		return new MethodMetadataBuilder(request.getDeclaredByMetadataId(), Modifier.ABSTRACT, methodMetadata.getMethodName(), methodReturnType, paramaterTypes, methodMetadata.getParameterNames(), new InvocableMemberBodyBuilder());
	}

	public void notify(String upstreamDependency, String downstreamDependency) {

		if (MetadataIdentificationUtils.isIdentifyingClass(downstreamDependency)) {
			Assert.isTrue(MetadataIdentificationUtils.getMetadataClass(upstreamDependency).equals(MetadataIdentificationUtils.getMetadataClass(PhysicalTypeIdentifier.getMetadataIdentiferType())), "Expected class-level notifications only for PhysicalTypeIdentifier (not '" + upstreamDependency + "')");
			
			ClassOrInterfaceTypeDetails cid = typeLocationService.getTypeDetails(upstreamDependency);
			if (cid == null) {
				return;
			}
			boolean processed = false;
			final List<JavaType> layerTypes = cid.getLayerEntities();
			if (!layerTypes.isEmpty()) {
				for (final ClassOrInterfaceTypeDetails request : typeLocationService.findClassesOrInterfaceDetailsWithAnnotation(ROO_GWT_REQUEST)) {
					final ClassOrInterfaceTypeDetails entity = gwtTypeService.lookupEntityFromRequest(request);
					if (entity != null && layerTypes.contains(entity.getName())) {
						JavaType typeName = PhysicalTypeIdentifier.getJavaType(request.getDeclaredByMetadataId());
						ContextualPath typePath = PhysicalTypeIdentifier.getPath(request.getDeclaredByMetadataId());
						downstreamDependency = GwtRequestMetadata.createIdentifier(typeName, typePath);
						processed = true;
						break;
					}
				}
			}
			if (!processed && MemberFindingUtils.getAnnotationOfType(cid.getAnnotations(), ROO_GWT_REQUEST) == null) {
				boolean found = false;
				for (ClassOrInterfaceTypeDetails classOrInterfaceTypeDetails : typeLocationService.findClassesOrInterfaceDetailsWithAnnotation(ROO_GWT_REQUEST)) {
					AnnotationMetadata annotationMetadata = GwtUtils.getFirstAnnotation(classOrInterfaceTypeDetails, GwtUtils.REQUEST_ANNOTATIONS);
					if (annotationMetadata != null) {
						AnnotationAttributeValue<?> attributeValue = annotationMetadata.getAttribute("value");
						if (attributeValue != null) {
							String mirrorName = GwtUtils.getStringValue(attributeValue);
							if (mirrorName != null && cid.getName().getFullyQualifiedTypeName().equals(mirrorName)) {
								found = true;
								JavaType typeName = PhysicalTypeIdentifier.getJavaType(classOrInterfaceTypeDetails.getDeclaredByMetadataId());
								ContextualPath typePath = PhysicalTypeIdentifier.getPath(classOrInterfaceTypeDetails.getDeclaredByMetadataId());
								downstreamDependency = GwtRequestMetadata.createIdentifier(typeName, typePath);
								break;
							}
						}
					}
				}
				if (!found) {
					return;
				}
			} else if (!processed) {
				// A physical Java type has changed, and determine what the corresponding local metadata identification string would have been
				JavaType typeName = PhysicalTypeIdentifier.getJavaType(upstreamDependency);
				ContextualPath typePath = PhysicalTypeIdentifier.getPath(upstreamDependency);
				downstreamDependency = GwtRequestMetadata.createIdentifier(typeName, typePath);
			}

			// We only need to proceed if the downstream dependency relationship is not already registered
			// (if it's already registered, the event will be delivered directly later on)
			if (metadataDependencyRegistry.getDownstream(upstreamDependency).contains(downstreamDependency)) {
				return;
			}
		}

		// We should now have an instance-specific "downstream dependency" that can be processed by this class
		Assert.isTrue(MetadataIdentificationUtils.getMetadataClass(downstreamDependency).equals(MetadataIdentificationUtils.getMetadataClass(getProvidesType())), "Unexpected downstream notification for '" + downstreamDependency + "' to this provider (which uses '" + getProvidesType() + "'");

		metadataService.get(downstreamDependency, true);
	}
}
