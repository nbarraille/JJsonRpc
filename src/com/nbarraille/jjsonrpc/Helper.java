package com.nbarraille.jjsonrpc;

import java.lang.reflect.Method;

/**
 * Helper that contains various methods.
 * @author nbarraille
 *
 */
public class Helper {
	
	/**
	 * Return true if the objects in the array are compatible with the types one by one.
	 */
	public static boolean areCompatible(Object[] obj, Class<?>[] types) {
		if(obj == null)
			return types.length == 0;
		
		if(obj.length != types.length)
			return false;
		
		for(int i = 0; i < obj.length; i++) {
			if(!isCompatible(obj[i], types[i]))
				return false;
		}
		
		return true;
	}

	private static boolean isCompatible(Object obj, Class<?> type) {
		// Null objects are compatible with everything except primitive types.
		if(obj == null)
			return !type.isPrimitive();
			
		// Returns true if obj is an instance of type or one of type's subclass (or implements type if type is an interface)
		if(type.isInstance(obj))
			return true;
		
		// Returns true if obj is the wrapper of type.
	    if(type.isPrimitive())
	        return isWrapperTypeOf(obj, type);
	    
	    return false;
	}

	
	private static Object castObjectTo(Object obj, Class<?> targetType) throws ClassCastException {
		// Dealing with null objects.
		if(obj == null) {
			if(targetType.isPrimitive()) {
				throw new ClassCastException("Primitive cannot be null.");
			} else {
				return null;
			}
		}
		
		// Dealing with castable objects
		if(targetType.isInstance(obj))
			return targetType.cast(obj);
		
		// Dealing with wrapper objects
		if(targetType.isPrimitive() && isWrapperTypeOf(obj, targetType))
			return obj; // Isn't Java 5 autoboxing awesome?
		
		return null;
	}
	
	/**
	 * Returns true if Object is an instance of the wrapper class of type, false else.
	 * @return
	 */
	private static boolean isWrapperTypeOf(Object obj, Class<?> type) {
	    try {
	        return !obj.getClass().isPrimitive() && obj.getClass().getDeclaredField("TYPE").get(null).equals(type);
	    } catch(NoSuchFieldException e){
	        return false;
	    } catch (IllegalArgumentException e) {
			e.printStackTrace();
			return false;
		} catch (SecurityException e) {
			e.printStackTrace();
			return false;
		} catch (IllegalAccessException e) {
			e.printStackTrace();
			return false;
		}
	}
	
	/**
	 * Prepares the parameters (re-cast) so they can be used with the given method.
	 * Returns null if one of the parameters wasn't compatible.
	 * @param params the params to prepare.
	 * @param method the method th prepare the params for.
	 * @return the prepared params.
	 */
	public static Object[] castParameters(Object[] params, Method method) {
		Object[] prepared = new Object[params.length];
		Class<?>[] types = method.getParameterTypes();
		
		if(params.length != types.length)
			return null;
		
		for(int i = 0; i < params.length; i++) {
			try {
				prepared[i] = castObjectTo(params[i], types[i]);
			} catch(ClassCastException e) {
				// Impossible to cast this parameter to this type.
				return null;
			}
		}
		
		return prepared;
	}
	
	/**
	 * Returns an array containing the class of each object.
	 * @param obj the array of objects.
	 * @return an array of their classes.
	 */
	public static Class<?>[] getClasses(Object[] obj) {
		if(obj == null)
			return null;
		
		Class<?>[] c = new Class<?>[obj.length];
		for(int i = 0; i < obj.length; i++) {
			c[i] = obj[i] == null ? Object.class : obj[i].getClass();
		}
		
		return c;
	}
}
