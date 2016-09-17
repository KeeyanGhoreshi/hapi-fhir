package ca.uhn.fhir.rest.method;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.*;

import org.apache.commons.lang3.Validate;
import org.hl7.fhir.instance.model.api.IIdType;
import org.springframework.beans.factory.xml.ResourceEntityResolver;

import ca.uhn.fhir.context.ConfigurationException;
import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.model.api.IResource;
import ca.uhn.fhir.rest.annotation.Patch;
import ca.uhn.fhir.rest.annotation.ResourceParam;
import ca.uhn.fhir.rest.api.PatchTypeEnum;
import ca.uhn.fhir.rest.api.RequestTypeEnum;
import ca.uhn.fhir.rest.api.RestOperationTypeEnum;
import ca.uhn.fhir.rest.client.BaseHttpClientInvocation;
import ca.uhn.fhir.rest.server.Constants;
import ca.uhn.fhir.rest.server.exceptions.InternalErrorException;
import ca.uhn.fhir.rest.server.exceptions.InvalidRequestException;

/**
 * Base class for an operation that has a resource type but not a resource body in the
 * request body
 *
 */
public class PatchMethodBinding extends BaseOutcomeReturningMethodBindingWithResourceIdButNoResourceBody {

	private int myPatchTypeParameterIndex = -1;
	private int myResourceParamIndex;

	public PatchMethodBinding(Method theMethod, FhirContext theContext, Object theProvider) {
		super(theMethod, theContext, theProvider, Patch.class, theMethod.getAnnotation(Patch.class).type());

		for (ListIterator<Parameter> iter = Arrays.asList(theMethod.getParameters()).listIterator(); iter.hasNext();) {
			int nextIndex = iter.nextIndex();
			Parameter next = iter.next();
			if (next.getType().equals(PatchTypeEnum.class)) {
				myPatchTypeParameterIndex = nextIndex;
			}
			if (next.getAnnotation(ResourceParam.class) != null) {
				myResourceParamIndex = nextIndex;
			}
		}

		if (myPatchTypeParameterIndex == -1) {
			throw new ConfigurationException("Method has no parameter of type " + PatchTypeEnum.class.getName() + " - " + theMethod.toString());
		}
		if (myResourceParamIndex == -1) {
			throw new ConfigurationException("Method has no parameter with @" + ResourceParam.class.getSimpleName() + " annotation - " + theMethod.toString());
		}
	}

	@Override
	public boolean incomingServerRequestMatchesMethod(RequestDetails theRequest) {
		boolean retVal = super.incomingServerRequestMatchesMethod(theRequest);
		if (retVal) {
			PatchTypeParameter.getTypeForRequestOrThrowInvalidRequestException(theRequest);
		}
		return retVal;
	}

	@Override
	public RestOperationTypeEnum getRestOperationType() {
		return RestOperationTypeEnum.PATCH;
	}

	@Override
	protected Set<RequestTypeEnum> provideAllowableRequestTypes() {
		return Collections.singleton(RequestTypeEnum.PATCH);
	}

	@Override
	protected BaseHttpClientInvocation createClientInvocation(Object[] theArgs, IResource theResource) {
		StringBuilder urlExtension = new StringBuilder();
		urlExtension.append(getContext().getResourceDefinition(theResource).getName());

		return new HttpPostClientInvocation(getContext(), theResource, urlExtension.toString());
	}

	@Override
	protected boolean allowVoidReturnType() {
		return true;
	}

	@Override
	public BaseHttpClientInvocation invokeClient(Object[] theArgs) throws InternalErrorException {
		IIdType idDt = (IIdType) theArgs[getIdParameterIndex()];
		if (idDt == null) {
			throw new NullPointerException("ID can not be null");
		}

		if (idDt.hasResourceType() == false) {
			idDt = idDt.withResourceType(getResourceName());
		} else if (getResourceName().equals(idDt.getResourceType()) == false) {
			throw new InvalidRequestException("ID parameter has the wrong resource type, expected '" + getResourceName() + "', found: " + idDt.getResourceType());
		}

		PatchTypeEnum patchType = (PatchTypeEnum) theArgs[myPatchTypeParameterIndex];
		String body = (String) theArgs[myResourceParamIndex];
		
		HttpPatchClientInvocation retVal = createPatchInvocation(getContext(), idDt, patchType, body);
		
		for (int idx = 0; idx < theArgs.length; idx++) {
			IParameter nextParam = getParameters().get(idx);
			nextParam.translateClientArgumentIntoQueryArgument(getContext(), theArgs[idx], null, null);
		}

		return retVal;
	}

	public static HttpPatchClientInvocation createPatchInvocation(FhirContext theContext, IIdType theId, PatchTypeEnum thePatchType, String theBody) {
		HttpPatchClientInvocation retVal = new HttpPatchClientInvocation(theContext, theId, thePatchType.getContentType(), theBody);
		return retVal;
	}

	@Override
	protected void addParametersForServerRequest(RequestDetails theRequest, Object[] theParams) {
		theParams[getIdParameterIndex()] = theRequest.getId();
	}

	@Override
	protected String getMatchingOperation() {
		return null;
	}

}