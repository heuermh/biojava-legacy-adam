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
package org.biojava.adam.convert;

import javax.annotation.concurrent.Immutable;

import org.bdgenomics.convert.AbstractConverter;
import org.bdgenomics.convert.ConversionException;
import org.bdgenomics.convert.ConversionStringency;

import org.bdgenomics.formats.avro.QualityScoreVariant;

import org.biojava.bio.program.fastq.FastqVariant;

import org.slf4j.Logger;

/**
 * Convert Biojava 1.x FastqVariant to bdg-formats QualityScoreVariant.
 *
 * @author  Michael Heuer
 */
@Immutable
final class FastqVariantToQualityScoreVariant extends AbstractConverter<FastqVariant, QualityScoreVariant> {

    /**
     * Package private no-arg constructor.
     */
    FastqVariantToQualityScoreVariant() {
        super(FastqVariant.class, QualityScoreVariant.class);
    }


    @Override
    public QualityScoreVariant convert(final FastqVariant fastqVariant,
                                       final ConversionStringency stringency,
                                       final Logger logger) throws ConversionException {

        if (fastqVariant == null) {
            warnOrThrow(fastqVariant, "must not be null", null, stringency, logger);
            return null;
        }
        if (fastqVariant.isIllumina()) {
            return QualityScoreVariant.FASTQ_ILLUMINA;
        }
        else if (fastqVariant.isSolexa()) {
            return QualityScoreVariant.FASTQ_SOLEXA;
        }
        return QualityScoreVariant.FASTQ_SANGER;
    }
}
