/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.commons.configuration.convert;

import java.lang.reflect.Array;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;

import org.apache.commons.configuration.interpol.ConfigurationInterpolator;
import org.apache.commons.lang3.ClassUtils;

/**
 * <p>
 * A default implementation of the {@code ConversionHandler} interface.
 * </p>
 * <p>
 * This class implements the standard data type conversions as used by
 * {@code AbstractConfiguration} and derived classes. There is a central
 * conversion method - {@code convert()} - for converting a passed in object to
 * a given target class. The basic implementation already handles a bunch of
 * standard data type conversions. If other conversions are to be supported,
 * this method can be overridden.
 * </p>
 * <p>
 * The object passed to {@code convert()} can be a single value or a complex
 * object (like an array, a collection, etc.) containing multiple values. It
 * lies in the responsibility of {@code convert()} to deal with such complex
 * objects. The implementation provided by this class tries to extract the first
 * child element and then delegates to {@code convertValue()} which does the
 * actual conversion.
 * </p>
 *
 * @version $Id$
 * @since 2.0
 */
public class DefaultConversionHandler implements ConversionHandler
{
    /** A helper object used for extracting values from complex objects. */
    private static final AbstractListDelimiterHandler EXTRACTOR =
            (AbstractListDelimiterHandler) DisabledListDelimiterHandler.INSTANCE;

    /**
     * Constant for a default {@code ConfigurationInterpolator} to be used if
     * none is provided by the caller.
     */
    private static final ConfigurationInterpolator NULL_INTERPOLATOR =
            new ConfigurationInterpolator()
            {
                @Override
                public Object interpolate(Object value)
                {
                    return value;
                };
            };

    public <T> T to(Object src, Class<T> targetCls, ConfigurationInterpolator ci)
    {
        ConfigurationInterpolator interpolator = fetchInterpolator(ci);
        return convert(interpolator.interpolate(src), targetCls, interpolator);
    }

    /**
     * {@inheritDoc} This implementation extracts all values stored in the
     * passed in source object, converts them to the target type, and adds them
     * to a result array. Arrays of objects and of primitive types are
     * supported. If the source object is <b>null</b>, result is <b>null</b>,
     * too.
     */
    public Object toArray(Object src, Class<?> elemClass,
            ConfigurationInterpolator ci)
    {
        if (src == null)
        {
            return null;
        }

        ConfigurationInterpolator interpolator = fetchInterpolator(ci);
        return elemClass.isPrimitive() ? toPrimitiveArray(src, elemClass,
                interpolator) : toObjectArray(src, elemClass, interpolator);
    }

    /**
     * {@inheritDoc} This implementation extracts all values stored in the
     * passed in source object, converts them to the target type, and adds them
     * to the target collection. The target collection must not be <b>null</b>.
     * If the source object is <b>null</b>, nothing is added to the collection.
     *
     * @throws IllegalArgumentException if the target collection is <b>null</b>
     */
    public <T> void toCollection(Object src, Class<T> elemClass,
            ConfigurationInterpolator ci, Collection<T> dest)
    {
        if (dest == null)
        {
            throw new IllegalArgumentException(
                    "Target collection must not be null!");
        }

        if (src != null)
        {
            ConfigurationInterpolator interpolator = fetchInterpolator(ci);
            convertToCollection(src, elemClass, interpolator, dest);
        }
    }

    /**
     * Tests whether the passed in object is complex (which means that it
     * contains multiple values). This method is called by
     * {@link #convert(Object, Class, ConfigurationInterpolator)} to figure out
     * whether a actions are required to extract a single value from a complex
     * source object. This implementation considers the following objects as
     * complex:
     * <ul>
     * <li>{@code Iterable} objects</li>
     * <li>{@code Iterator} objects</li>
     * <li>Arrays</li>
     * </ul>
     *
     * @param src the source object
     * @return <b>true</b> if this is a complex object, <b>false</b> otherwise
     */
    protected boolean isComplexObject(Object src)
    {
        return src instanceof Iterator<?> || src instanceof Iterable<?>
                || (src != null && src.getClass().isArray());
    }

    /**
     * Performs the conversion from the passed in source object to the specified
     * target class. This method is called for each conversion to be done. The
     * source object has already been passed to the
     * {@link ConfigurationInterpolator}, so interpolation does not have to be
     * done again. (The passed in {@code ConfigurationInterpolator} may still be
     * necessary for extracting values from complex objects; it is guaranteed to
     * be non <b>null</b>.) The source object may be a complex object, e.g. a
     * collection or an array. This base implementation checks whether the
     * source object is complex. If so, it delegates to
     * {@link #extractConversionValue(Object, Class, ConfigurationInterpolator)}
     * to obtain a single value. Eventually,
     * {@link #convertValue(Object, Class, ConfigurationInterpolator)} is called
     * with the single value to be converted.
     *
     * @param <T> the desired target type of the conversion
     * @param src the source object to be converted
     * @param targetCls the desired target class
     * @param ci the {@code ConfigurationInterpolator} (not <b>null</b>)
     * @return the converted value
     * @throws ConversionException if conversion is not possible
     */
    protected <T> T convert(Object src, Class<T> targetCls,
            ConfigurationInterpolator ci)
    {
        Object conversionSrc =
                isComplexObject(src) ? extractConversionValue(src, targetCls,
                        ci) : src;
        return convertValue(ci.interpolate(conversionSrc), targetCls, ci);
    }

    /**
     * Extracts a maximum number of values contained in the given source object
     * and returns them as flat collection. This method is useful if the caller
     * only needs a subset of values, e.g. only the first one.
     *
     * @param source the source object (may be a single value or a complex
     *        object)
     * @param limit the number of elements to extract
     * @return a collection with all extracted values
     */
    protected Collection<?> extractValues(Object source, int limit)
    {
        return EXTRACTOR.flatten(source, limit);
    }

    /**
     * Extracts all values contained in the given source object and returns them
     * as a flat collection.
     *
     * @param source the source object (may be a single value or a complex
     *        object)
     * @return a collection with all extracted values
     */
    protected Collection<?> extractValues(Object source)
    {
        return extractValues(source, Integer.MAX_VALUE);
    }

    /**
     * Extracts a single value from a complex object. This method is called by
     * {@code convert()} if the source object is complex. This implementation
     * extracts the first value from the complex object and returns it.
     *
     * @param container the complex object
     * @param targetCls the target class of the conversion
     * @param ci the {@code ConfigurationInterpolator} (not <b>null</b>)
     * @return the value to be converted (may be <b>null</b> if no values are
     *         found)
     */
    protected Object extractConversionValue(Object container,
            Class<?> targetCls, ConfigurationInterpolator ci)
    {
        Collection<?> values = extractValues(container, 1);
        return values.isEmpty() ? null : ci.interpolate(values.iterator()
                .next());
    }

    /**
     * Performs a conversion of a single value to the specified target class.
     * The passed in source object is guaranteed to be a single value, but it
     * can be <b>null</b>. Derived classes that want to extend the available
     * conversions, but are happy with the handling of complex objects, just
     * need to override this method.
     *
     * @param <T> the desired target type of the conversion
     * @param src the source object (a single value)
     * @param targetCls the target class of the conversion
     * @param ci the {@code ConfigurationInterpolator} (not <b>null</b>)
     * @return the converted value
     * @throws ConversionException if conversion is not possible
     */
    protected <T> T convertValue(Object src, Class<T> targetCls,
            ConfigurationInterpolator ci)
    {
        if (src == null)
        {
            return null;
        }

        return targetCls.cast(PropertyConverter.to(targetCls, src,
                new Object[0]));
    }

    /**
     * Converts the given source object to an array of objects.
     *
     * @param src the source object
     * @param elemClass the element class of the array
     * @param ci the {@code ConfigurationInterpolator}
     * @return the result array
     * @throws ConversionException if a conversion cannot be performed
     */
    private <T> T[] toObjectArray(Object src, Class<T> elemClass,
            ConfigurationInterpolator ci)
    {
        Collection<T> convertedCol = new LinkedList<T>();
        convertToCollection(src, elemClass, ci, convertedCol);
        // Safe to cast because the element class is specified
        @SuppressWarnings("unchecked")
        T[] result = (T[]) Array.newInstance(elemClass, convertedCol.size());
        return convertedCol.toArray(result);
    }

    /**
     * Converts the given source object to an array of a primitive type. This
     * method performs some checks whether the source object is already an array
     * of the correct type or a corresponding wrapper type. If not, all values
     * are extracted, converted one by one, and stored in a newly created array.
     *
     * @param src the source object
     * @param elemClass the element class of the array
     * @param ci the {@code ConfigurationInterpolator}
     * @return the result array
     * @throws ConversionException if a conversion cannot be performed
     */
    private Object toPrimitiveArray(Object src, Class<?> elemClass,
            ConfigurationInterpolator ci)
    {
        if (src.getClass().isArray())
        {
            if (src.getClass().getComponentType().equals(elemClass))
            {
                return src;
            }

            if (src.getClass().getComponentType()
                    .equals(ClassUtils.primitiveToWrapper(elemClass)))
            {
                // the value is an array of the wrapper type derived from the
                // specified primitive type
                int length = Array.getLength(src);
                Object array = Array.newInstance(elemClass, length);

                for (int i = 0; i < length; i++)
                {
                    Array.set(array, i, Array.get(src, i));
                }
                return array;
            }
        }

        Collection<?> values = extractValues(src);
        Class<?> targetClass = ClassUtils.primitiveToWrapper(elemClass);
        Object array = Array.newInstance(elemClass, values.size());
        int idx = 0;
        for (Object value : values)
        {
            Array.set(array, idx++,
                    convertValue(ci.interpolate(value), targetClass, ci));
        }
        return array;
    }

    /**
     * Helper method for converting all values of a source object and storing
     * them in a collection.
     *
     * @param <T> the target type of the conversion
     * @param src the source object
     * @param elemClass the target class of the conversion
     * @param ci the {@code ConfigurationInterpolator}
     * @param dest the collection in which to store the results
     * @throws ConversionException if a conversion cannot be performed
     */
    private <T> void convertToCollection(Object src, Class<T> elemClass,
            ConfigurationInterpolator ci, Collection<T> dest)
    {
        for (Object o : extractValues(ci.interpolate(src)))
        {
            dest.add(convert(o, elemClass, ci));
        }
    }

    /**
     * Obtains a {@code ConfigurationInterpolator}. If the passed in one is not
     * <b>null</b>, it is used. Otherwise, a default one is returned.
     *
     * @param ci the {@code ConfigurationInterpolator} provided by the caller
     * @return the {@code ConfigurationInterpolator} to be used
     */
    private static ConfigurationInterpolator fetchInterpolator(
            ConfigurationInterpolator ci)
    {
        return (ci != null) ? ci : NULL_INTERPOLATOR;
    }
}