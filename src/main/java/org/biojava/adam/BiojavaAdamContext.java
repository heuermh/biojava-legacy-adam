/*

    biojava-legacy-adam  BioJava 1.x (biojava-legacy) and ADAM integration.
    Copyright (c) 2013-2022 held jointly by the individual authors.

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

import org.apache.hadoop.io.compress.CompressionCodec;
import org.apache.hadoop.io.compress.CompressionCodecFactory;

import org.apache.spark.SparkContext;

import org.apache.spark.rdd.RDD;

import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.JavaSparkContext;

import org.bdgenomics.adam.ds.ADAMContext;

import org.bdgenomics.adam.ds.feature.FeatureDataset;

import org.bdgenomics.adam.ds.read.ReadDataset;

import org.bdgenomics.adam.ds.sequence.SequenceDataset;

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

    /** SLF4J logger. */
    private final Logger logger;

    /**
     * Create a new BiojavaAdamContext with the specified Spark context.
     *
     * @param sc Spark context, must not be null
     */
    public BiojavaAdamContext(final SparkContext sc) {        
        super(sc);

        javaSparkContext = new JavaSparkContext(sc);
        logger = logger().logger();

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
        CompressionCodecFactory codecFactory = new CompressionCodecFactory(javaSparkContext.hadoopConfiguration());
        CompressionCodec codec = codecFactory.getCodec(path);

        if (codec == null) {
            return fileSystem.open(path);
        }
        return codec.createInputStream(fileSystem.open(path));            
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
     * Load the specified path in FASTQ format as reads with Biojava 1.x (biojava-legacy).
     * Alias for <code>loadBiojavaFastqReads</code>.
     *
     * @param path path in FASTQ format, must not be null
     * @return dataset of reads
     * @throws IOException if an I/O error occurs
     */
    public ReadDataset loadFastqReads(final String path) throws IOException {
        return loadBiojavaFastqReads(path);
    }

    /**
     * Load the specified path in FASTQ format as reads with Biojava 1.x (biojava-legacy).
     *
     * @param path path in FASTQ format, must not be null
     * @return dataset of reads
     * @throws IOException if an I/O error occurs
     */
    public ReadDataset loadBiojavaFastqReads(final String path) throws IOException {
        logger.info("Loading " + path + " in FASTQ format as reads with Biojava 1.x (biojava-legacy)...");
        FastqReader fastqReader = new SangerFastqReader();
        try (InputStream inputStream = inputStream(path)) {
            JavaRDD<Fastq> fastqs = javaSparkContext.parallelize(collect(fastqReader.read(inputStream)));
            JavaRDD<Read> reads = fastqs.map(fastq -> readConverter.convert(fastq, ConversionStringency.STRICT, logger));
            return ReadDataset.apply(reads.rdd());
        }
    }

    /**
     * Load the specified path in FASTA format as DNA sequences.
     *
     * @param path path in FASTA format, must not be null
     * @return dataset of DNA sequences
     * @throws IOException if an I/O error occurs
     */
    public SequenceDataset loadBiojavaFastaDna(final String path) throws IOException {
        logger.info("Loading " + path + " in FASTA format as DNA sequences with Biojava 1.x (biojava-legacy)...");
        try (BufferedReader reader = reader(path)) {
            JavaRDD<org.biojava.bio.seq.Sequence> biojavaSequences = javaSparkContext.parallelize(collect(SeqIOTools.readFastaDNA(reader)));
            JavaRDD<Sequence> sequences = biojavaSequences.map(biojavaSequence -> sequenceConverter.convert(biojavaSequence, ConversionStringency.STRICT, logger));
            return SequenceDataset.apply(sequences.rdd());
        }
    }

    /**
     * Load the specified path in FASTA format as RNA sequences.
     *
     * @param path path in FASTA format, must not be null
     * @return dataset of RNA sequences
     * @throws IOException if an I/O error occurs
     */
    public SequenceDataset loadBiojavaFastaRna(final String path) throws IOException {
        logger.info("Loading " + path + " in FASTA format as RNA sequences with Biojava 1.x (biojava-legacy)...");
        try (BufferedReader reader = reader(path)) {
            JavaRDD<org.biojava.bio.seq.Sequence> biojavaSequences = javaSparkContext.parallelize(collect(SeqIOTools.readFastaRNA(reader)));
            JavaRDD<Sequence> sequences = biojavaSequences.map(biojavaSequence -> sequenceConverter.convert(biojavaSequence, ConversionStringency.STRICT, logger));
            return SequenceDataset.apply(sequences.rdd());
        }
    }

    /**
     * Load the specified path in FASTA format as protein sequences.
     *
     * @param path path in FASTA format, must not be null
     * @return dataset of protein sequences
     * @throws IOException if an I/O error occurs
     */
    public SequenceDataset loadBiojavaFastaProtein(final String path) throws IOException {
        logger.info("Loading " + path + " in FASTA format as protein sequences with Biojava 1.x (biojava-legacy)...");
        try (BufferedReader reader = reader(path)) {
            JavaRDD<org.biojava.bio.seq.Sequence> biojavaSequences = javaSparkContext.parallelize(collect(SeqIOTools.readFastaProtein(reader)));
            JavaRDD<Sequence> sequences = biojavaSequences.map(biojavaSequence -> sequenceConverter.convert(biojavaSequence, ConversionStringency.STRICT, logger));
            return SequenceDataset.apply(sequences.rdd());
        }
    }


    /**
     * Load the specified path in FASTA format as DNA sequences using biojavax RichSequence.
     * Alias for <code>loadBiojavaRichFastaDna</code>.
     *
     * @param path path in FASTA format, must not be null
     * @return dataset of DNA sequences
     * @throws IOException if an I/O error occurs
     */
    public SequenceDataset loadRichFastaDna(final String path) throws IOException {
        return loadBiojavaRichFastaDna(path);
    }

    /**
     * Load the specified path in FASTA format as DNA sequences using biojavax RichSequence.
     *
     * @param path path in FASTA format, must not be null
     * @return dataset of DNA sequences
     * @throws IOException if an I/O error occurs
     */
    public SequenceDataset loadBiojavaRichFastaDna(final String path) throws IOException {
        logger.info("Loading " + path + " in FASTA format as DNA sequences with Biojava 1.x (biojava-legacy)...");
        try (BufferedReader reader = reader(path)) {
            JavaRDD<RichSequence> richSequences = javaSparkContext.parallelize(collect(RichSequence.IOTools.readFastaDNA(reader, null)));
            JavaRDD<Sequence> sequences = richSequences.map(richSequence -> richSequenceConverter.convert(richSequence, ConversionStringency.STRICT, logger));
            return SequenceDataset.apply(sequences.rdd());
        }
    }

    /**
     * Load the specified path in FASTA format as RNA sequences using biojavax RichSequence.
     * Alias for <code>loadBiojavaRichFastaRna</code>.
     *
     * @param path path in FASTA format, must not be null
     * @return dataset of RNA sequences
     * @throws IOException if an I/O error occurs
     */
    public SequenceDataset loadRichFastaRna(final String path) throws IOException {
        return loadBiojavaRichFastaRna(path);
    }

    /**
     * Load the specified path in FASTA format as RNA sequences using biojavax RichSequence.
     *
     * @param path path in FASTA format, must not be null
     * @return dataset of RNA sequences
     * @throws IOException if an I/O error occurs
     */
    public SequenceDataset loadBiojavaRichFastaRna(final String path) throws IOException {
        logger.info("Loading " + path + " in FASTA format as RNA sequences with Biojava 1.x (biojava-legacy)...");
        try (BufferedReader reader = reader(path)) {
            JavaRDD<RichSequence> richSequences = javaSparkContext.parallelize(collect(RichSequence.IOTools.readFastaRNA(reader, null)));
            JavaRDD<Sequence> sequences = richSequences.map(richSequence -> richSequenceConverter.convert(richSequence, ConversionStringency.STRICT, logger));
            return SequenceDataset.apply(sequences.rdd());
        }
    }

    /**
     * Load the specified path in FASTA format as protein sequences using biojavax RichSequence.
     * Alias for <code>loadBiojavaRichFastaProtein</code>.
     *
     * @param path path in FASTA format, must not be null
     * @return dataset of protein sequences
     * @throws IOException if an I/O error occurs
     */
    public SequenceDataset loadRichFastaProtein(final String path) throws IOException {
        return loadBiojavaRichFastaProtein(path);
    }

    /**
     * Load the specified path in FASTA format as protein sequences using biojavax RichSequence.
     *
     * @param path path in FASTA format, must not be null
     * @return dataset of protein sequences
     * @throws IOException if an I/O error occurs
     */
    public SequenceDataset loadBiojavaRichFastaProtein(final String path) throws IOException {
        logger.info("Loading " + path + " in FASTA format as protein sequences with Biojava 1.x (biojava-legacy)...");
        try (BufferedReader reader = reader(path)) {
            JavaRDD<RichSequence> richSequences = javaSparkContext.parallelize(collect(RichSequence.IOTools.readFastaProtein(reader, null)));
            JavaRDD<Sequence> sequences = richSequences.map(richSequence -> richSequenceConverter.convert(richSequence, ConversionStringency.STRICT, logger));
            return SequenceDataset.apply(sequences.rdd());
        }
    }


    /**
     * Load the specified path in Genbank format as DNA sequences using biojavax RichSequence.
     * Alias for <code>loadBiojavaGenbankDna</code>.
     *
     * @param path path in Genbank format, must not be null
     * @return dataset of DNA sequences
     * @throws IOException if an I/O error occurs
     */
    public SequenceDataset loadGenbankDna(final String path) throws IOException {
        return loadBiojavaGenbankDna(path);
    }

    /**
     * Load the specified path in Genbank format as DNA sequences using biojavax RichSequence.
     *
     * @param path path in Genbank format, must not be null
     * @return dataset of DNA sequences
     * @throws IOException if an I/O error occurs
     */
    public SequenceDataset loadBiojavaGenbankDna(final String path) throws IOException {
        logger.info("Loading " + path + " in Genbank format as DNA sequences with Biojava 1.x (biojava-legacy)...");
        try (BufferedReader reader = reader(path)) {
            JavaRDD<RichSequence> richSequences = javaSparkContext.parallelize(collect(RichSequence.IOTools.readGenbankDNA(reader, null)));
            JavaRDD<Sequence> sequences = richSequences.map(richSequence -> richSequenceConverter.convert(richSequence, ConversionStringency.STRICT, logger));
            return SequenceDataset.apply(sequences.rdd());
        }
    }

    /**
     * Load the specified path in Genbank format as RNA sequences using biojavax RichSequence.
     * Alias for <code>loadBiojavaGenbankRna</code>.
     *
     * @param path path in Genbank format, must not be null
     * @return dataset of RNA sequences
     * @throws IOException if an I/O error occurs
     */
    public SequenceDataset loadGenbankRna(final String path) throws IOException {
        return loadBiojavaGenbankRna(path);
    }

    /**
     * Load the specified path in Genbank format as RNA sequences using biojavax RichSequence.
     *
     * @param path path in Genbank format, must not be null
     * @return dataset of RNA sequences
     * @throws IOException if an I/O error occurs
     */
    public SequenceDataset loadBiojavaGenbankRna(final String path) throws IOException {
        logger.info("Loading " + path + " in Genbank format as RNA sequences with Biojava 1.x (biojava-legacy)...");
        try (BufferedReader reader = reader(path)) {
            JavaRDD<RichSequence> richSequences = javaSparkContext.parallelize(collect(RichSequence.IOTools.readGenbankRNA(reader, null)));
            JavaRDD<Sequence> sequences = richSequences.map(richSequence -> richSequenceConverter.convert(richSequence, ConversionStringency.STRICT, logger));
            return SequenceDataset.apply(sequences.rdd());
        }
    }

    /**
     * Load the specified path in Genbank format as protein sequences using biojavax RichSequence.
     * Alias for <code>loadBiojavaGenbankProtein</code>.
     *
     * @param path path in Genbank format, must not be null
     * @return dataset of protein sequences
     * @throws IOException if an I/O error occurs
     */
    public SequenceDataset loadGenbankProtein(final String path) throws IOException {
        return loadBiojavaGenbankProtein(path);
    }

    /**
     * Load the specified path in Genbank format as protein sequences using biojavax RichSequence.
     *
     * @param path path in Genbank format, must not be null
     * @return dataset of protein sequences
     * @throws IOException if an I/O error occurs
     */
    public SequenceDataset loadBiojavaGenbankProtein(final String path) throws IOException {
        logger.info("Loading " + path + " in Genbank format as protein sequences with Biojava 1.x (biojava-legacy)...");
        try (BufferedReader reader = reader(path)) {
            JavaRDD<RichSequence> richSequences = javaSparkContext.parallelize(collect(RichSequence.IOTools.readGenbankProtein(reader, null)));
            JavaRDD<Sequence> sequences = richSequences.map(richSequence -> richSequenceConverter.convert(richSequence, ConversionStringency.STRICT, logger));
            return SequenceDataset.apply(sequences.rdd());
        }
    }


    /**
     * Load the specified path in EMBL format as DNA sequences using biojavax RichSequence.
     * Alias for <code>loadBiojavaEmblDna</code>.
     *
     * @param path path in EMBL format, must not be null
     * @return dataset of DNA sequences
     * @throws IOException if an I/O error occurs
     */
    public SequenceDataset loadEmblDna(final String path) throws IOException {
        return loadBiojavaEmblDna(path);
    }

    /**
     * Load the specified path in EMBL format as DNA sequences using biojavax RichSequence.
     *
     * @param path path in EMBL format, must not be null
     * @return dataset of DNA sequences
     * @throws IOException if an I/O error occurs
     */
    public SequenceDataset loadBiojavaEmblDna(final String path) throws IOException {
        logger.info("Loading " + path + " in EMBL format as DNA sequences with Biojava 1.x (biojava-legacy)...");
        try (BufferedReader reader = reader(path)) {
            JavaRDD<RichSequence> richSequences = javaSparkContext.parallelize(collect(RichSequence.IOTools.readEMBLDNA(reader, null)));
            JavaRDD<Sequence> sequences = richSequences.map(richSequence -> richSequenceConverter.convert(richSequence, ConversionStringency.STRICT, logger));
            return SequenceDataset.apply(sequences.rdd());
        }
    }

    /**
     * Load the specified path in EMBL format as RNA sequences using biojavax RichSequence.
     * Alias for <code>loadBiojavaEmblRna</code>.
     *
     * @param path path in EMBL format, must not be null
     * @return dataset of RNA sequences
     * @throws IOException if an I/O error occurs
     */
    public SequenceDataset loadEmblRna(final String path) throws IOException {
        return loadBiojavaEmblRna(path);
    }

    /**
     * Load the specified path in EMBL format as RNA sequences using biojavax RichSequence.
     *
     * @param path path in EMBL format, must not be null
     * @return dataset of RNA sequences
     * @throws IOException if an I/O error occurs
     */
    public SequenceDataset loadBiojavaEmblRna(final String path) throws IOException {
        logger.info("Loading " + path + " in EMBL format as RNA sequences with Biojava 1.x (biojava-legacy)...");
        try (BufferedReader reader = reader(path)) {
            JavaRDD<RichSequence> richSequences = javaSparkContext.parallelize(collect(RichSequence.IOTools.readEMBLRNA(reader, null)));
            JavaRDD<Sequence> sequences = richSequences.map(richSequence -> richSequenceConverter.convert(richSequence, ConversionStringency.STRICT, logger));
            return SequenceDataset.apply(sequences.rdd());
        }
    }

    /**
     * Load the specified path in EMBL format as protein sequences using biojavax RichSequence.
     * Alias for <code>loadBiojavaEmblProtein</code>.
     *
     * @param path path in EMBL format, must not be null
     * @return dataset of protein sequences
     * @throws IOException if an I/O error occurs
     */
    public SequenceDataset loadEmblProtein(final String path) throws IOException {
        return loadBiojavaEmblProtein(path);
    }

    /**
     * Load the specified path in EMBL format as protein sequences using biojavax RichSequence.
     *
     * @param path path in EMBL format, must not be null
     * @return dataset of protein sequences
     * @throws IOException if an I/O error occurs
     */
    public SequenceDataset loadBiojavaEmblProtein(final String path) throws IOException {
        logger.info("Loading " + path + " in EMBL format as protein sequences with Biojava 1.x (biojava-legacy)...");
        try (BufferedReader reader = reader(path)) {
            JavaRDD<RichSequence> richSequences = javaSparkContext.parallelize(collect(RichSequence.IOTools.readEMBLProtein(reader, null)));
            JavaRDD<Sequence> sequences = richSequences.map(richSequence -> richSequenceConverter.convert(richSequence, ConversionStringency.STRICT, logger));
            return SequenceDataset.apply(sequences.rdd());
        }
    }
    

    /**
     * Load the specified path in Genbank format as DNA sequence features using biojavax RichSequence.
     * Alias for <code>loadBiojavaGenbankDnaFeatures</code>.
     *
     * @param path path in Genkbank format, must not be null
     * @return dataset of DNA sequence features
     * @throws IOException if an I/O error occurs
     */
    public FeatureDataset loadGenbankDnaFeatures(final String path) throws IOException {
        return loadBiojavaGenbankDnaFeatures(path);
    }

    /**
     * Load the specified path in Genbank format as DNA sequence features using biojavax RichSequence.
     *
     * @param path path in Genkbank format, must not be null
     * @return dataset of DNA sequence features
     * @throws IOException if an I/O error occurs
     */
    public FeatureDataset loadBiojavaGenbankDnaFeatures(final String path) throws IOException {
        logger.info("Loading " + path + " in Genbank format as DNA sequence features with Biojava 1.x (biojava-legacy)...");
        try (BufferedReader reader = reader(path)) {
            JavaRDD<RichSequence> richSequences = javaSparkContext.parallelize(collect(RichSequence.IOTools.readGenbankDNA(reader, null)));
            JavaRDD<Feature> features = richSequences.flatMap(richSequence -> featureConverter.convert(richSequence, ConversionStringency.STRICT, logger).iterator());
            return FeatureDataset.apply(features.rdd());
        }
    }

    /**
     * Load the specified path in Genbank format as RNA sequence features using biojavax RichSequence.
     * Alias for <code>loadBiojavaGenbankRnaFeatures</code>.
     *
     * @param path path in Genkbank format, must not be null
     * @return dataset of RNA sequence features
     * @throws IOException if an I/O error occurs
     */
    public FeatureDataset loadGenbankRnaFeatures(final String path) throws IOException {
        return loadBiojavaGenbankRnaFeatures(path);
    }

    /**
     * Load the specified path in Genbank format as RNA sequence features using biojavax RichSequence.
     *
     * @param path path in Genkbank format, must not be null
     * @return dataset of RNA sequence features
     * @throws IOException if an I/O error occurs
     */
    public FeatureDataset loadBiojavaGenbankRnaFeatures(final String path) throws IOException {
        logger.info("Loading " + path + " in Genbank format as RNA sequence features with Biojava 1.x (biojava-legacy)...");
        try (BufferedReader reader = reader(path)) {
            JavaRDD<RichSequence> richSequences = javaSparkContext.parallelize(collect(RichSequence.IOTools.readGenbankRNA(reader, null)));
            JavaRDD<Feature> features = richSequences.flatMap(richSequence -> featureConverter.convert(richSequence, ConversionStringency.STRICT, logger).iterator());
            return FeatureDataset.apply(features.rdd());
        }
        catch (Exception e) {
            e.printStackTrace();
            throw e;
        }
    }

    /**
     * Load the specified path in Genbank format as protein sequence features using biojavax RichSequence.
     * Alias for <code>loadBiojavaGenbankProteinFeatures</code>.
     *
     * @param path path in Genkbank format, must not be null
     * @return dataset of protein sequence features
     * @throws IOException if an I/O error occurs
     */
    public FeatureDataset loadGenbankProteinFeatures(final String path) throws IOException {
        return loadBiojavaGenbankProteinFeatures(path);
    }

    /**
     * Load the specified path in Genbank format as protein sequence features using biojavax RichSequence.
     *
     * @param path path in Genkbank format, must not be null
     * @return dataset of protein sequence features
     * @throws IOException if an I/O error occurs
     */
    public FeatureDataset loadBiojavaGenbankProteinFeatures(final String path) throws IOException {
        logger.info("Loading " + path + " in Genbank format as protein sequence features with Biojava 1.x (biojava-legacy)...");
        try (BufferedReader reader = reader(path)) {
            JavaRDD<RichSequence> richSequences = javaSparkContext.parallelize(collect(RichSequence.IOTools.readGenbankProtein(reader, null)));
            JavaRDD<Feature> features = richSequences.flatMap(richSequence -> featureConverter.convert(richSequence, ConversionStringency.STRICT, logger).iterator());
            return FeatureDataset.apply(features.rdd());
        }
    }


    /**
     * Load the specified path in EMBL format as DNA sequence features using biojavax RichSequence.
     * Alias for <code>loadBiojavaEmblDnaFeatures</code>.
     *
     * @param path path in Genkbank format, must not be null
     * @return dataset of DNA sequence features
     * @throws IOException if an I/O error occurs
     */
    public FeatureDataset loadEmblDnaFeatures(final String path) throws IOException {
        return loadBiojavaEmblDnaFeatures(path);
    }

    /**
     * Load the specified path in EMBL format as DNA sequence features using biojavax RichSequence.
     *
     * @param path path in Genkbank format, must not be null
     * @return dataset of DNA sequence features
     * @throws IOException if an I/O error occurs
     */
    public FeatureDataset loadBiojavaEmblDnaFeatures(final String path) throws IOException {
        logger.info("Loading " + path + " in EMBL format as DNA sequence features with Biojava 1.x (biojava-legacy)...");
        try (BufferedReader reader = reader(path)) {
            JavaRDD<RichSequence> richSequences = javaSparkContext.parallelize(collect(RichSequence.IOTools.readEMBLDNA(reader, null)));
            JavaRDD<Feature> features = richSequences.flatMap(richSequence -> featureConverter.convert(richSequence, ConversionStringency.STRICT, logger).iterator());
            return FeatureDataset.apply(features.rdd());
        }
    }

    /**
     * Load the specified path in EMBL format as RNA sequence features using biojavax RichSequence.
     * Alias for <code>loadBiojavaEmblRnaFeatures</code>.
     *
     * @param path path in Genkbank format, must not be null
     * @return dataset of RNA sequence features
     * @throws IOException if an I/O error occurs
     */
    public FeatureDataset loadEmblRnaFeatures(final String path) throws IOException {
        return loadBiojavaEmblRnaFeatures(path);
    }

    /**
     * Load the specified path in EMBL format as RNA sequence features using biojavax RichSequence.
     *
     * @param path path in Genkbank format, must not be null
     * @return dataset of RNA sequence features
     * @throws IOException if an I/O error occurs
     */
    public FeatureDataset loadBiojavaEmblRnaFeatures(final String path) throws IOException {
        logger.info("Loading " + path + " in EMBL format as RNA sequence features with Biojava 1.x (biojava-legacy)...");
        try (BufferedReader reader = reader(path)) {
            JavaRDD<RichSequence> richSequences = javaSparkContext.parallelize(collect(RichSequence.IOTools.readEMBLRNA(reader, null)));
            JavaRDD<Feature> features = richSequences.flatMap(richSequence -> featureConverter.convert(richSequence, ConversionStringency.STRICT, logger).iterator());
            return FeatureDataset.apply(features.rdd());
        }
    }

    /**
     * Load the specified path in EMBL format as protein sequence features using biojavax RichSequence.
     * Alias for <code>loadBiojavaEmblProteinFeatures</code>.
     *
     * @param path path in Genkbank format, must not be null
     * @return dataset of protein sequence features
     * @throws IOException if an I/O error occurs
     */
    public FeatureDataset loadEmblProteinFeatures(final String path) throws IOException {
        return loadBiojavaEmblProteinFeatures(path);
    }

    /**
     * Load the specified path in EMBL format as protein sequence features using biojavax RichSequence.
     *
     * @param path path in Genkbank format, must not be null
     * @return dataset of protein sequence features
     * @throws IOException if an I/O error occurs
     */
    public FeatureDataset loadBiojavaEmblProteinFeatures(final String path) throws IOException {
        logger.info("Loading " + path + " in EMBL format as protein sequence features with Biojava 1.x (biojava-legacy)...");
        try (BufferedReader reader = reader(path)) {
            JavaRDD<RichSequence> richSequences = javaSparkContext.parallelize(collect(RichSequence.IOTools.readEMBLProtein(reader, null)));
            JavaRDD<Feature> features = richSequences.flatMap(richSequence -> featureConverter.convert(richSequence, ConversionStringency.STRICT, logger).iterator());
            return FeatureDataset.apply(features.rdd());
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
