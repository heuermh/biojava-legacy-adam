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
 * Convert bdg-formats QualityScoreVariant to Biojava 1.x FastqVariant.
 *
 * @author  Michael Heuer
 */
@Immutable
final class QualityScoreVariantToFastqVariant extends AbstractConverter<QualityScoreVariant, FastqVariant> {

    /**
     * Package private no-arg constructor.
     */
    QualityScoreVariantToFastqVariant() {
        super(QualityScoreVariant.class, FastqVariant.class);
    }


    @Override
    public FastqVariant convert(final QualityScoreVariant qualityScoreVariant,
                                final ConversionStringency stringency,
                                final Logger logger) throws ConversionException {

        if (qualityScoreVariant == null) {
            warnOrThrow(qualityScoreVariant, "must not be null", null, stringency, logger);
            return null;
        }
        if (qualityScoreVariant == QualityScoreVariant.FASTQ_ILLUMINA) {
            return FastqVariant.FASTQ_ILLUMINA;
        }
        else if (qualityScoreVariant == QualityScoreVariant.FASTQ_SOLEXA) {
            return FastqVariant.FASTQ_SOLEXA;
        }
        return FastqVariant.FASTQ_SANGER;
    }
}
