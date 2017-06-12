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

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;

import java.util.ArrayList;
import java.util.List;

import com.google.common.collect.ImmutableList;

import com.google.inject.Injector;
import com.google.inject.Guice;
import com.google.inject.Key;
import com.google.inject.TypeLiteral;

import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;

import org.apache.spark.SparkContext;

import org.apache.spark.rdd.RDD;

import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.JavaSparkContext;

import org.apache.spark.storage.StorageLevel;

import org.bdgenomics.adam.rdd.ADAMContext;

import org.bdgenomics.adam.rdd.feature.FeatureRDD;

import org.bdgenomics.adam.rdd.sequence.ReadRDD;
import org.bdgenomics.adam.rdd.sequence.SequenceRDD;

import org.bdgenomics.convert.Converter;
import org.bdgenomics.convert.ConversionException;
import org.bdgenomics.convert.ConversionStringency;

import org.bdgenomics.convert.bdgenomics.BdgenomicsModule;

import org.bdgenomics.formats.avro.Feature;
import org.bdgenomics.formats.avro.Read;
import org.bdgenomics.formats.avro.Sequence;

import org.biojava.bio.BioException;

import org.biojava.bio.seq.SequenceIterator;

import org.biojava.bio.seq.io.SeqIOTools;

import org.biojava.bio.program.fastq.Fastq;
import org.biojava.bio.program.fastq.FastqReader;
import org.biojava.bio.program.fastq.SangerFastqReader;

import org.biojavax.bio.seq.RichSequence;
import org.biojavax.bio.seq.RichSequenceIterator;

import org.biojava.adam.convert.BiojavaModule;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import scala.Some;

/**
 * Extends ADAMContext with load methods for Biojava 1.x (biojava-legacy) models.
 */
public class BiojavaAdamContext extends ADAMContext {
    /** Java Spark context. */
    private final transient JavaSparkContext javaSparkContext;

    /** Convert Biojava 1.x Fastq to bdg-formats Read. */
    private final Converter<Fastq, Read> readConverter;

    /** Convert Biojava 1.x Sequence to bdg-formats Sequence. */
    private final Converter<org.biojava.bio.seq.Sequence, Sequence> sequenceConverter;

    /** Convert Biojava 1.x RichSequence to bdg-formats Sequence. */
    private final Converter<RichSequence, Sequence> richSequenceConverter;

    /** Convert Biojava 1.x RichSequence to a list of bdg-formats Features. */
    private final Converter<RichSequence, List<Feature>> featureConverter;


    /**
     * Create a new BiojavaAdamContext with the specified Spark context.
     *
     * @param sc Spark context, must not be null
     */
    public BiojavaAdamContext(final SparkContext sc) {        
        super(sc);

        javaSparkContext = new JavaSparkContext(sc);

        Injector injector = Guice.createInjector(new BiojavaModule(), new BdgenomicsModule());
        readConverter = injector.getInstance(Key.get(new TypeLiteral<Converter<Fastq, Read>>() {}));
        sequenceConverter = injector.getInstance(Key.get(new TypeLiteral<Converter<org.biojava.bio.seq.Sequence, Sequence>>() {}));
        richSequenceConverter = injector.getInstance(Key.get(new TypeLiteral<Converter<RichSequence, Sequence>>() {}));
        featureConverter = injector.getInstance(Key.get(new TypeLiteral<Converter<RichSequence, List<Feature>>>() {}));
    }


    /**
     * Create and return an InputStream for the HDFS path represented by the specified file name.
     *
     * @param fileName file name, must not be null
     * @return an InputStream for the HDFS path represented by the specified file name
     * @throws IOException if an I/O error occurs
     */
    InputStream inputStream(final String fileName) throws IOException {
        checkNotNull(fileName);
        Path path = new Path(fileName);
        FileSystem fileSystem = path.getFileSystem(javaSparkContext.hadoopConfiguration());
        return fileSystem.open(path);
    }

    /**
     * Create and return a BufferedReader for the HDFS path represented by the specified file name.
     *
     * @param fileName file name, must not be null
     * @return a BufferedReader for the HDFS path represented by the specified file name
     * @throws IOException if an I/O error occurs
     */
    BufferedReader reader(final String fileName) throws IOException {
        return new BufferedReader(new InputStreamReader(inputStream(fileName)));
    }

    /**
     * Load the specified path in FASTQ format as reads.
     *
     * @param path path in FASTQ format, must not be null
     * @return RDD of reads
     * @throws IOException if an I/O error occurs
     */
    public ReadRDD loadFastq(final String path) throws IOException {
        log().info("Loading " + path + " in FASTQ format as reads...");
        FastqReader fastqReader = new SangerFastqReader();
        try (InputStream inputStream = inputStream(path)) {
            JavaRDD<Fastq> fastqs = javaSparkContext.parallelize(collect(fastqReader.read(inputStream)));
            JavaRDD<Read> reads = fastqs.map(fastq -> readConverter.convert(fastq, ConversionStringency.STRICT, log()));
            return ReadRDD.apply(reads.rdd());
        }
    }

    /**
     * Load the specified path in FASTA format as DNA sequences.
     *
     * @param path path in FASTA format, must not be null
     * @return RDD of DNA sequences
     * @throws IOException if an I/O error occurs
     */
    public SequenceRDD loadFastaDna(final String path) throws IOException {
        log().info("Loading " + path + " in FASTA format as DNA sequences...");
        try (BufferedReader reader = reader(path)) {
            JavaRDD<org.biojava.bio.seq.Sequence> biojavaSequences = javaSparkContext.parallelize(collect(SeqIOTools.readFastaDNA(reader)));
            JavaRDD<Sequence> sequences = biojavaSequences.map(biojavaSequence -> sequenceConverter.convert(biojavaSequence, ConversionStringency.STRICT, log()));
            return SequenceRDD.apply(sequences.rdd());
        }
    }

    /**
     * Load the specified path in FASTA format as RNA sequences.
     *
     * @param path path in FASTA format, must not be null
     * @return RDD of RNA sequences
     * @throws IOException if an I/O error occurs
     */
    public SequenceRDD loadFastaRna(final String path) throws IOException {
        log().info("Loading " + path + " in FASTA format as RNA sequences...");
        try (BufferedReader reader = reader(path)) {
            JavaRDD<org.biojava.bio.seq.Sequence> biojavaSequences = javaSparkContext.parallelize(collect(SeqIOTools.readFastaRNA(reader)));
            JavaRDD<Sequence> sequences = biojavaSequences.map(biojavaSequence -> sequenceConverter.convert(biojavaSequence, ConversionStringency.STRICT, log()));
            return SequenceRDD.apply(sequences.rdd());
        }
    }

    /**
     * Load the specified path in FASTA format as protein sequences.
     *
     * @param path path in FASTA format, must not be null
     * @return RDD of protein sequences
     * @throws IOException if an I/O error occurs
     */
    public SequenceRDD loadFastaProtein(final String path) throws IOException {
        log().info("Loading " + path + " in FASTA format as protein sequences...");
        try (BufferedReader reader = reader(path)) {
            JavaRDD<org.biojava.bio.seq.Sequence> biojavaSequences = javaSparkContext.parallelize(collect(SeqIOTools.readFastaProtein(reader)));
            JavaRDD<Sequence> sequences = biojavaSequences.map(biojavaSequence -> sequenceConverter.convert(biojavaSequence, ConversionStringency.STRICT, log()));
            return SequenceRDD.apply(sequences.rdd());
        }
    }


    /**
     * Load the specified path in FASTA format as DNA sequences using biojavax RichSequence.
     *
     * @param path path in FASTA format, must not be null
     * @return RDD of DNA sequences
     * @throws IOException if an I/O error occurs
     */
    public SequenceRDD loadRichFastaDna(final String path) throws IOException {
        log().info("Loading " + path + " in FASTA format as DNA sequences...");
        try (BufferedReader reader = reader(path)) {
            JavaRDD<RichSequence> richSequences = javaSparkContext.parallelize(collect(RichSequence.IOTools.readFastaDNA(reader, null)));
            JavaRDD<Sequence> sequences = richSequences.map(richSequence -> richSequenceConverter.convert(richSequence, ConversionStringency.STRICT, log()));
            return SequenceRDD.apply(sequences.rdd());
        }
    }

    /**
     * Load the specified path in FASTA format as RNA sequences using biojavax RichSequence.
     *
     * @param path path in FASTA format, must not be null
     * @return RDD of RNA sequences
     * @throws IOException if an I/O error occurs
     */
    public SequenceRDD loadRichFastaRna(final String path) throws IOException {
        log().info("Loading " + path + " in FASTA format as RNA sequences...");
        try (BufferedReader reader = reader(path)) {
            JavaRDD<RichSequence> richSequences = javaSparkContext.parallelize(collect(RichSequence.IOTools.readFastaRNA(reader, null)));
            JavaRDD<Sequence> sequences = richSequences.map(richSequence -> richSequenceConverter.convert(richSequence, ConversionStringency.STRICT, log()));
            return SequenceRDD.apply(sequences.rdd());
        }
    }

    /**
     * Load the specified path in FASTA format as protein sequences using biojavax RichSequence.
     *
     * @param path path in FASTA format, must not be null
     * @return RDD of protein sequences
     * @throws IOException if an I/O error occurs
     */
    public SequenceRDD loadRichFastaProtein(final String path) throws IOException {
        log().info("Loading " + path + " in FASTA format as protein sequences...");
        try (BufferedReader reader = reader(path)) {
            JavaRDD<RichSequence> richSequences = javaSparkContext.parallelize(collect(RichSequence.IOTools.readFastaProtein(reader, null)));
            JavaRDD<Sequence> sequences = richSequences.map(richSequence -> richSequenceConverter.convert(richSequence, ConversionStringency.STRICT, log()));
            return SequenceRDD.apply(sequences.rdd());
        }
    }


    /**
     * Load the specified path in Genbank format as DNA sequences using biojavax RichSequence.
     *
     * @param path path in Genbank format, must not be null
     * @return RDD of DNA sequences
     * @throws IOException if an I/O error occurs
     */
    public SequenceRDD loadGenbankDna(final String path) throws IOException {
        log().info("Loading " + path + " in Genbank format as DNA sequences...");
        try (BufferedReader reader = reader(path)) {
            JavaRDD<RichSequence> richSequences = javaSparkContext.parallelize(collect(RichSequence.IOTools.readGenbankDNA(reader, null)));
            JavaRDD<Sequence> sequences = richSequences.map(richSequence -> richSequenceConverter.convert(richSequence, ConversionStringency.STRICT, log()));
            return SequenceRDD.apply(sequences.rdd());
        }
    }

    /**
     * Load the specified path in Genbank format as RNA sequences using biojavax RichSequence.
     *
     * @param path path in Genbank format, must not be null
     * @return RDD of RNA sequences
     * @throws IOException if an I/O error occurs
     */
    public SequenceRDD loadGenbankRna(final String path) throws IOException {
        log().info("Loading " + path + " in Genbank format as RNA sequences...");
        try (BufferedReader reader = reader(path)) {
            JavaRDD<RichSequence> richSequences = javaSparkContext.parallelize(collect(RichSequence.IOTools.readGenbankRNA(reader, null)));
            JavaRDD<Sequence> sequences = richSequences.map(richSequence -> richSequenceConverter.convert(richSequence, ConversionStringency.STRICT, log()));
            return SequenceRDD.apply(sequences.rdd());
        }
    }

    /**
     * Load the specified path in Genbank format as protein sequences using biojavax RichSequence.
     *
     * @param path path in Genbank format, must not be null
     * @return RDD of protein sequences
     * @throws IOException if an I/O error occurs
     */
    public SequenceRDD loadGenbankProtein(final String path) throws IOException {
        log().info("Loading " + path + " in Genbank format as protein sequences...");
        try (BufferedReader reader = reader(path)) {
            JavaRDD<RichSequence> richSequences = javaSparkContext.parallelize(collect(RichSequence.IOTools.readGenbankProtein(reader, null)));
            JavaRDD<Sequence> sequences = richSequences.map(richSequence -> richSequenceConverter.convert(richSequence, ConversionStringency.STRICT, log()));
            return SequenceRDD.apply(sequences.rdd());
        }
    }


    /**
     * Load the specified path in EMBL format as DNA sequences using biojavax RichSequence.
     *
     * @param path path in EMBL format, must not be null
     * @return RDD of DNA sequences
     * @throws IOException if an I/O error occurs
     */
    public SequenceRDD loadEmblDna(final String path) throws IOException {
        log().info("Loading " + path + " in EMBL format as DNA sequences...");
        try (BufferedReader reader = reader(path)) {
            JavaRDD<RichSequence> richSequences = javaSparkContext.parallelize(collect(RichSequence.IOTools.readEMBLDNA(reader, null)));
            JavaRDD<Sequence> sequences = richSequences.map(richSequence -> richSequenceConverter.convert(richSequence, ConversionStringency.STRICT, log()));
            return SequenceRDD.apply(sequences.rdd());
        }
    }

    /**
     * Load the specified path in EMBL format as RNA sequences using biojavax RichSequence.
     *
     * @param path path in EMBL format, must not be null
     * @return RDD of RNA sequences
     * @throws IOException if an I/O error occurs
     */
    public SequenceRDD loadEmblRna(final String path) throws IOException {
        log().info("Loading " + path + " in EMBL format as RNA sequences...");
        try (BufferedReader reader = reader(path)) {
            JavaRDD<RichSequence> richSequences = javaSparkContext.parallelize(collect(RichSequence.IOTools.readEMBLRNA(reader, null)));
            JavaRDD<Sequence> sequences = richSequences.map(richSequence -> richSequenceConverter.convert(richSequence, ConversionStringency.STRICT, log()));
            return SequenceRDD.apply(sequences.rdd());
        }
    }

    /**
     * Load the specified path in EMBL format as protein sequences using biojavax RichSequence.
     *
     * @param path path in EMBL format, must not be null
     * @return RDD of protein sequences
     * @throws IOException if an I/O error occurs
     */
    public SequenceRDD loadEmblProtein(final String path) throws IOException {
        log().info("Loading " + path + " in EMBL format as protein sequences...");
        try (BufferedReader reader = reader(path)) {
            JavaRDD<RichSequence> richSequences = javaSparkContext.parallelize(collect(RichSequence.IOTools.readEMBLProtein(reader, null)));
            JavaRDD<Sequence> sequences = richSequences.map(richSequence -> richSequenceConverter.convert(richSequence, ConversionStringency.STRICT, log()));
            return SequenceRDD.apply(sequences.rdd());
        }
    }
    

    /**
     * Load the specified path in Genbank format as DNA sequence features using biojavax RichSequence.
     *
     * @param path path in Genkbank format, must not be null
     * @return RDD of DNA sequence features
     * @throws IOException if an I/O error occurs
     */
    public FeatureRDD loadGenbankDnaFeatures(final String path) throws IOException {
        log().info("Loading " + path + " in Genbank format as DNA sequence features...");
        try (BufferedReader reader = reader(path)) {
            JavaRDD<RichSequence> richSequences = javaSparkContext.parallelize(collect(RichSequence.IOTools.readGenbankDNA(reader, null)));
            JavaRDD<Feature> features = richSequences.flatMap(richSequence -> featureConverter.convert(richSequence, ConversionStringency.STRICT, log()).iterator());
            return FeatureRDD.apply(features.rdd(), new Some(StorageLevel.MEMORY_ONLY()));
        }
    }

    /**
     * Load the specified path in Genbank format as RNA sequence features using biojavax RichSequence.
     *
     * @param path path in Genkbank format, must not be null
     * @return RDD of RNA sequence features
     * @throws IOException if an I/O error occurs
     */
    public FeatureRDD loadGenbankRnaFeatures(final String path) throws IOException {
        log().info("Loading " + path + " in Genbank format as RNA sequence features...");
        try (BufferedReader reader = reader(path)) {
            JavaRDD<RichSequence> richSequences = javaSparkContext.parallelize(collect(RichSequence.IOTools.readGenbankRNA(reader, null)));
            JavaRDD<Feature> features = richSequences.flatMap(richSequence -> featureConverter.convert(richSequence, ConversionStringency.STRICT, log()).iterator());
            return FeatureRDD.apply(features.rdd(), new Some(StorageLevel.MEMORY_ONLY()));
        }
    }

    /**
     * Load the specified path in Genbank format as protein sequence features using biojavax RichSequence.
     *
     * @param path path in Genkbank format, must not be null
     * @return RDD of protein sequence features
     * @throws IOException if an I/O error occurs
     */
    public FeatureRDD loadGenbankProteinFeatures(final String path) throws IOException {
        log().info("Loading " + path + " in Genbank format as protein sequence features...");
        try (BufferedReader reader = reader(path)) {
            JavaRDD<RichSequence> richSequences = javaSparkContext.parallelize(collect(RichSequence.IOTools.readGenbankProtein(reader, null)));
            JavaRDD<Feature> features = richSequences.flatMap(richSequence -> featureConverter.convert(richSequence, ConversionStringency.STRICT, log()).iterator());
            return FeatureRDD.apply(features.rdd(), new Some(StorageLevel.MEMORY_ONLY()));
        }
    }


    /**
     * Load the specified path in EMBL format as DNA sequence features using biojavax RichSequence.
     *
     * @param path path in Genkbank format, must not be null
     * @return RDD of DNA sequence features
     * @throws IOException if an I/O error occurs
     */
    public FeatureRDD loadEmblDnaFeatures(final String path) throws IOException {
        log().info("Loading " + path + " in EMBL format as DNA sequence features...");
        try (BufferedReader reader = reader(path)) {
            JavaRDD<RichSequence> richSequences = javaSparkContext.parallelize(collect(RichSequence.IOTools.readEMBLDNA(reader, null)));
            JavaRDD<Feature> features = richSequences.flatMap(richSequence -> featureConverter.convert(richSequence, ConversionStringency.STRICT, log()).iterator());
            return FeatureRDD.apply(features.rdd(), new Some(StorageLevel.MEMORY_ONLY()));
        }
    }

    /**
     * Load the specified path in EMBL format as RNA sequence features using biojavax RichSequence.
     *
     * @param path path in Genkbank format, must not be null
     * @return RDD of RNA sequence features
     * @throws IOException if an I/O error occurs
     */
    public FeatureRDD loadEmblRnaFeatures(final String path) throws IOException {
        log().info("Loading " + path + " in EMBL format as RNA sequence features...");
        try (BufferedReader reader = reader(path)) {
            JavaRDD<RichSequence> richSequences = javaSparkContext.parallelize(collect(RichSequence.IOTools.readEMBLRNA(reader, null)));
            JavaRDD<Feature> features = richSequences.flatMap(richSequence -> featureConverter.convert(richSequence, ConversionStringency.STRICT, log()).iterator());
            return FeatureRDD.apply(features.rdd(), new Some(StorageLevel.MEMORY_ONLY()));
        }
    }

    /**
     * Load the specified path in EMBL format as protein sequence features using biojavax RichSequence.
     *
     * @param path path in Genkbank format, must not be null
     * @return RDD of protein sequence features
     * @throws IOException if an I/O error occurs
     */
    public FeatureRDD loadEmblProteinFeatures(final String path) throws IOException {
        log().info("Loading " + path + " in EMBL format as protein sequence features...");
        try (BufferedReader reader = reader(path)) {
            JavaRDD<RichSequence> richSequences = javaSparkContext.parallelize(collect(RichSequence.IOTools.readEMBLProtein(reader, null)));
            JavaRDD<Feature> features = richSequences.flatMap(richSequence -> featureConverter.convert(richSequence, ConversionStringency.STRICT, log()).iterator());
            return FeatureRDD.apply(features.rdd(), new Some(StorageLevel.MEMORY_ONLY()));
        }
    }

    /**
     * Collect the sequences in the specified iterator to a list.
     *
     * @param iterator iterator to collect
     * @return the sequences in the specified iterator collected to a list
     * @throws IOException if an I/O error occurs
     */
    static List<org.biojava.bio.seq.Sequence> collect(final SequenceIterator iterator) {
        List<org.biojava.bio.seq.Sequence> sequences = new ArrayList<org.biojava.bio.seq.Sequence>();
        try {
            while (iterator.hasNext()) {
                sequences.add(iterator.nextSequence());
            }
        }
        catch (BioException e) {
            // ignore
        }
        return sequences;
    }

    /**
     * Collect the specified iterable into a list.
     *
     * @param iterable iterable to collect
     * @return the specified iterable collected into a list
     */
    static <T> List<T> collect(final Iterable<T> iterable) {
        return ImmutableList.copyOf(iterable);
    }

    /**
     * Collect the rich sequences in the specified iterator to a list.
     *
     * @param iterator iterator to collect
     * @return the rich sequences in the specified iterator collected to a list
     * @throws IOException if an I/O error occurs
     */
    static List<RichSequence> collect(final RichSequenceIterator iterator) {
        List<RichSequence> sequences = new ArrayList<RichSequence>();
        try {
            while (iterator.hasNext()) {
                sequences.add(iterator.nextRichSequence());
            }
        }
        catch (BioException e) {
            // ignore
        }
        return sequences;
    }
}
