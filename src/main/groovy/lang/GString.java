/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package groovy.lang;

import org.codehaus.groovy.runtime.GStringImpl;
import org.codehaus.groovy.runtime.InvokerHelper;
import org.codehaus.groovy.runtime.StringGroovyMethods;

import java.io.IOException;
import java.io.Serializable;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Represents a String which contains embedded values such as "hello there
 * ${user} how are you?" which can be evaluated lazily. Advanced users can
 * iterate over the text and values to perform special processing, such as for
 * performing SQL operations, the values can be substituted for ? and the
 * actual value objects can be bound to a JDBC statement. The lovely name of
 * this class was suggested by Jules Gosnell and was such a good idea, I
 * couldn't resist :)
 *
 * @author <a href="mailto:james@coredevelopers.net">James Strachan</a>
 */
public abstract class GString extends GroovyObjectSupport implements Comparable, CharSequence, Writable, Buildable, Serializable {

    static final long serialVersionUID = -2638020355892246323L;

    /**
     * A GString containing a single empty String and no values.
     */
    public static final GString EMPTY = new GString(new Object[0]) {
        @Override
        public String[] getStrings() {
            return new String[]{""};
        }
    };
    public static final String[] EMPTY_STRING_ARRAY = new String[0];
    public static final Object[] EMPTY_OBJECT_ARRAY = new Object[0];

    private final Object[] values;

    public GString(Object values) {
        this.values = (Object[]) values;
    }

    public GString(Object[] values) {
        this.values = values;
    }

    // will be static in an instance

    public abstract String[] getStrings();

    /**
     * Overloaded to implement duck typing for Strings
     * so that any method that can't be evaluated on this
     * object will be forwarded to the toString() object instead.
     */
    @Override
    public Object invokeMethod(String name, Object args) {
        try {
            return super.invokeMethod(name, args);
        }
        catch (MissingMethodException e) {
            // lets try invoke the method on the real String
            return InvokerHelper.invokeMethod(toString(), name, args);
        }
    }

    public Object[] getValues() {
        return values;
    }

    public GString plus(GString that) {
        List<String> stringList = new ArrayList<String>(Arrays.asList(getStrings()));
        List<Object> valueList = new ArrayList<Object>(Arrays.asList(getValues()));

        List<String> thatStrings = Arrays.asList(that.getStrings());

        int stringListSize = stringList.size();
        if (stringListSize > valueList.size()) {
            thatStrings = new ArrayList<String>(thatStrings);
            // merge onto end of previous GString to avoid an empty bridging value
            int lastIndexOfStringList = stringListSize - 1;
            String s = stringList.get(lastIndexOfStringList);
            s += thatStrings.remove(0);
            stringList.set(lastIndexOfStringList, s);
        }

        stringList.addAll(thatStrings);
        valueList.addAll(Arrays.asList(that.getValues()));

        final String[] newStrings = stringList.toArray(EMPTY_STRING_ARRAY);
        final Object[] newValues = valueList.toArray();

        return new GStringImpl(newValues, newStrings);
    }

    public GString plus(String that) {
        return plus(new GStringImpl(EMPTY_OBJECT_ARRAY, new String[] { that }));
    }

    public int getValueCount() {
        return values.length;
    }

    public Object getValue(int idx) {
        return values[idx];
    }

    @Override
    public String toString() {
        StringWriter buffer = new StringWriter();
        try {
            writeTo(buffer);
        }
        catch (IOException e) {
            throw new StringWriterIOException(e);
        }
        return buffer.toString();
    }

    @Override
    public Writer writeTo(Writer out) throws IOException {
        String[] s = getStrings();
        int numberOfValues = values.length;
        for (int i = 0, size = s.length; i < size; i++) {
            out.write(s[i]);
            if (i < numberOfValues) {
                final Object value = values[i];

                if (value instanceof Closure) {
                    final Closure c = (Closure) value;

                    if (c.getMaximumNumberOfParameters() == 0) {
                        InvokerHelper.write(out, c.call());
                    } else if (c.getMaximumNumberOfParameters() == 1) {
                        c.call(out);
                    } else {
                        throw new GroovyRuntimeException("Trying to evaluate a GString containing a Closure taking "
                                + c.getMaximumNumberOfParameters() + " parameters");
                    }
                } else {
                    InvokerHelper.write(out, value);
                }
            }
        }
        return out;
    }

    /* (non-Javadoc)
     * @see groovy.lang.Buildable#build(groovy.lang.GroovyObject)
     */

    @Override
    public void build(final GroovyObject builder) {
        final String[] s = getStrings();
        final int numberOfValues = values.length;

        for (int i = 0, size = s.length; i < size; i++) {
            builder.getProperty("mkp");
            builder.invokeMethod("yield", new Object[]{s[i]});
            if (i < numberOfValues) {
                builder.getProperty("mkp");
                builder.invokeMethod("yield", new Object[]{values[i]});
            }
        }
    }

    @Override
    public int hashCode() {
        return 37 + toString().hashCode();
    }

    @Override
    public boolean equals(Object that) {
        if (that instanceof GString) {
            return equals((GString) that);
        }
        return false;
    }

    public boolean equals(GString that) {
        return toString().equals(that.toString());
    }

    @Override
    public int compareTo(Object that) {
        return toString().compareTo(that.toString());
    }

    @Override
    public char charAt(int index) {
        return toString().charAt(index);
    }

    @Override
    public int length() {
        return toString().length();
    }

    @Override
    public CharSequence subSequence(int start, int end) {
        return toString().subSequence(start, end);
    }

    /**
     * Turns a String into a regular expression pattern
     *
     * @return the regular expression pattern
     */
    public Pattern negate() {
        return StringGroovyMethods.bitwiseNegate(toString());
    }

    public byte[] getBytes() {
        return toString().getBytes();
    }

    public byte[] getBytes(String charset) throws UnsupportedEncodingException {
       return toString().getBytes(charset);
    }
}
