/*

    biojava-legacy adam  BioJava 1.x (biojava-legacy) and ADAM integration.
    Copyright (c) 2013-2017 held jointly by the individual authors.

    This library is free software; you can redistribute it and/or modify it
    under the terms of the GNU Lesser General Public License as published
    by the Free Software Foundation; either version 3 of the License, or (at
    your option) any later version.

    This library is distributed in the hope that it will be useful, but WITHOUT
    ANY WARRANTY; with out even the implied warranty of MERCHANTABILITY or
    FITNESS FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public
    License for more details.

    You should have received a copy of the GNU Lesser General Public License
    along with this library;  if not, write to the Free Software Foundation,
    Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307  USA.

    > http://www.fsf.org/licensing/licenses/lgpl.html
    > http://www.opensource.org/licenses/lgpl-license.php

*/
package org.biojava.adam;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamClass;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.KryoException;
import com.esotericsoftware.kryo.KryoSerializable;
import com.esotericsoftware.kryo.Serializer;

import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;

import com.esotericsoftware.kryo.serializers.BeanSerializer;
import com.esotericsoftware.kryo.serializers.FieldSerializer;

import com.esotericsoftware.kryo.util.ObjectMap;

import com.google.common.collect.ImmutableListMultimap;

import org.biojava.bio.seq.DNATools;
import org.biojava.bio.seq.RNATools;
import org.biojava.bio.seq.ProteinTools;

import org.biojava.bio.symbol.Alphabet;
import org.biojava.bio.symbol.AlphabetManager;
//import org.biojava.bio.symbol.FiniteAlphabet;

//import org.biojava.utils.ListTools;
//import org.biojava.utils.SingletonList;

import org.biojavax.bio.seq.SimpleRichSequence;

import org.bdgenomics.adam.serialization.ADAMKryoRegistrator;

/**
 * Biojava kryo registrator.
 *
 * @author  Michael Heuer
 */
public class BiojavaKryoRegistrator extends ADAMKryoRegistrator {

    @Override
    public void registerClasses(final Kryo kryo) {
        super.registerClasses(kryo);

        kryo.register(DNATools.getDNA().getClass(), new Serializer() {
            @Override
            public void write(final Kryo kryo, final Output output, final Object object) {
                output.writeString(((Alphabet) object).getName());
            }
            @Override
            public Object read(final Kryo kryo, final Input input, final Class cls) {
                String name = input.readString();
                return "protein".equals(name) ? ProteinTools.getTAlphabet() : AlphabetManager.alphabetForName(name);
            }
        });

        /*
        kryo.register(SimpleRichSequence.class, new FieldSerializer<SimpleRichSequence>(kryo, SimpleRichSequence.class) {
            @Override
            public SimpleRichSequence read(final Kryo kryo, final Input input, final Class<SimpleRichSequence> cls) {
                SimpleRichSequence simpleRichSequence = super.read(kryo, input, cls);
                System.out.println("read simpleRichSequence " + simpleRichSequence);
                try {
                    simpleRichSequence.checkMakeSequence();
                    System.out.println("after checkMakeSequence " + simpleRichSequence.getInternalSymbolList());
                    System.out.println("seqString " + simpleRichSequence.seqString().substring(0, 30));
                }
                catch (Exception e) {
                    System.out.println("could not checkMakeSequence, caught " + e);
                    e.printStackTrace();
                }
                return simpleRichSequence;
            }
        });
        */

        /*
        kryo.register(SimpleRichSequence.class, new Serializer<SimpleRichSequence>() {
            @Override
            public void write(final Kryo kryo, final Output output, final SimpleRichSequence simpleRichSequence) {
            }

            @Override
            public SimpleRichSequence read(final Kryo kryo, final Input input, final Class<SimpleRichSequence> cls) {
            }
        });
        */
    /*

        wip...

        // kryo.register(SimpleRichSequence.class, new BeanSerializer(kryo, SimpleRichSequence.class));

        System.out.println("registering custom serializer for " + DNATools.getDNA().getClass() + "...");
        kryo.register(DNATools.getDNA().getClass(), new Serializer() {
            @Override
            public void write(final Kryo kryo, final Output output, final Object object) {
                // empty
            }
            @Override
            public Object read(final Kryo kryo, final Input input, final Class cls) {
                System.out.println("returing DNATools.getDNA() singleton instance...");
                return DNATools.getDNA();
            }
        });

        //kryo.register(SimpleRichSequence.class, new JavaSerializer());
        //kryo.setClassLoader(Thread.currentThread().getContextClassLoader());
        //kryo.register(ImmutableListMultimap.class, new JavaSerializer());
        //kryo.register(SingletonList.class, new JavaSerializer());
        //kryo.register(Alphabet.class, new JavaSerializer());
        //kryo.register(AlphabetManager.class, new JavaSerializer());

        kryo.register(Alphabet.class, new Serializer<Alphabet>() {
            @Override
            public void write(final Kryo kryo, final Output output, final Alphabet alphabet) {
                System.out.println("writing alphabet " + alphabet + "...");
                output.writeString(alphabet.getName());
            }
            @Override
            public Alphabet read(final Kryo kryo, final Input input, final Class cls) {
                String name = input.readString();
                System.out.println("read alphabet " + name + "...");
                return "protein".equals(name) ? ProteinTools.getTAlphabet() : AlphabetManager.alphabetForName(name);
            }
        });
        kryo.register(AlphabetManager.class, new Serializer<AlphabetManager>() {
            @Override
            public void write(final Kryo kryo, final Output output, final AlphabetManager alphabetManager) {
                // empty
                System.out.println("ignoring AlphabetManager write...");
            }

            @Override
            public AlphabetManager read(final Kryo kryo, final Input input, final Class cls) {
                System.out.println("returning singleton AlphabetManager instance...");
                return AlphabetManager.instance();
            }
        });
        kryo.register(SingletonList.class, new JavaSerializer());
        kryo.register(ListTools.Doublet.class, new JavaSerializer());
        kryo.register(ListTools.Triplet.class, new JavaSerializer());
        kryo.register(java.util.Collections.nCopies(1, new Object()).getClass(), new JavaSerializer());
    }

    private static class JavaSerializer extends Serializer {
        @Override
        public void write(final Kryo kryo, final Output output, final Object object) {
            try {
                ObjectMap graphContext = kryo.getGraphContext();
                ObjectOutputStream objectStream = (ObjectOutputStream) graphContext.get(this);
                if (objectStream == null) {
                    objectStream = new ObjectOutputStream(output);
                    graphContext.put(this, objectStream);
                }
                objectStream.writeObject(object);
                objectStream.flush();
            }
            catch (Exception ex) {
                throw new KryoException("Error during Java serialization.", ex);
            }
        }

        @Override
        public Object read(final Kryo kryo, final Input input, final Class type) {
            try {
                ObjectMap graphContext = kryo.getGraphContext();
                ObjectInputStream objectStream = (ObjectInputStream) graphContext.get(this);
                if (objectStream == null) {
                    objectStream = new ObjectInputStreamWithKryoClassLoader(input, kryo);
                    graphContext.put(this, objectStream);
                }
                return objectStream.readObject();
            }
            catch (Exception ex) {
                throw new KryoException("Error during Java deserialization.", ex);
            }
        }
    }

    private static class ObjectInputStreamWithKryoClassLoader extends ObjectInputStream {
        private final ClassLoader loader;

        ObjectInputStreamWithKryoClassLoader(final InputStream in, final Kryo kryo) throws IOException {
            super(in);
            this.loader = kryo.getClassLoader();
        }

        @Override
        protected Class<?> resolveClass(final ObjectStreamClass desc) {
            try {
                return Class.forName(desc.getName(), false, loader);
            }
            catch (ClassNotFoundException e) {
                throw new RuntimeException("Class not found: " + desc.getName(), e);
            }
        }
    }
    */
    }
}
