package com.nbarraille.jjsonrpc;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;

/**
 * A callback method contains all the information to call a specific method.
 * @author nbarraille
 *
 */
public class CallbackMethod {
	private Method _method; // The method to call back.
	private Object _instance; // The instance of the object to call the method on.
	private Object[] _params; // The parameters to call the method with (just have to add the response at the end)
	
	
	/**
	 * Creates a callback method. If it is not valid, throws an InvalidMethodException. A callback method's last argument should
	 * always be of type Object, so that the result of the request can be provided to the method. The method can have several other
	 * parameters before that, but they must be provided when the CallbackMethod is instantiated.
	 * @param method the method to call.
	 * @param instance the instance of the object to call the method on (should be null for static methods)
	 * @param previousParams the parameters to provide to the callback method before the response.
	 * @throws InvalidMethodException if the callback is not valid, with an explanatory message.
	 */
	public CallbackMethod(Method method, Object instance, Object[] previousParams) throws InvalidMethodException {
		if(method == null)
			throw new InvalidMethodException("The callback method cannot be null");
		
		if(method.getExceptionTypes().length > 0)
			throw new InvalidMethodException("The callback method cannot throw exceptions");
		
		if(Modifier.isStatic(method.getModifiers()) && instance != null)
			throw new InvalidMethodException("You cannot provide a static method and a non null instance");
		
		if(!Modifier.isStatic(method.getModifiers()) && instance == null)
			throw new InvalidMethodException("You cannot provide a non static method and a null instance");
		
		if(method.getParameterTypes()[method.getParameterTypes().length - 1] != Object.class)
			throw new InvalidMethodException("The callback method last parameter must be an Object");
		
		if(!Helper.areCompatible(previousParams, Arrays.copyOfRange(method.getParameterTypes(), 0,
				method.getParameterTypes().length - 1)))
			throw new InvalidMethodException("The provided arguments are not of valid types.");
		
		Object[] params;
		if(previousParams == null) {
			params = new Object[1];
		} else {
			params = new Object[previousParams.length + 1];
			System.arraycopy(previousParams, 0, params, 0, previousParams.length);
		}
		
		if(instance != null) {
			try {
				instance.getClass().getMethod(method.getName(), Helper.getClasses(params));
			} catch(NoSuchMethodException e) {
				throw new InvalidMethodException("The provided method does not belongs to the provided class");
			}
		}
		
		_method = method;
		_instance = instance;
		_params = params;
	}
	
	public Method getMethod() {
		return _method;
	}
	
	public Object getInstance() {
		return _instance;
	}
	
	public Object[] getParams() {
		return _params;
	}
	
	/**
	 * Invokes the callback method with the provided response.
	 * @param response the response of the request.
	 */
	public void run(Object response) {
		_params[_params.length - 1] = response;
		try {
			_method.invoke(_instance, _params);
		} catch (IllegalArgumentException e) {
			// Should not happen
			e.printStackTrace();
		} catch (IllegalAccessException e) {
			// Should not happen
			e.printStackTrace();
		} catch (InvocationTargetException e) {
			// Should not happen
			e.printStackTrace();
		}
	}
}
