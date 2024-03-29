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
package org.biojava.adam.convert;

import javax.annotation.concurrent.Immutable;

import org.bdgenomics.convert.AbstractConverter;
import org.bdgenomics.convert.ConversionException;
import org.bdgenomics.convert.ConversionStringency;

import org.biojava.bio.seq.DNATools;
import org.biojava.bio.seq.ProteinTools;
import org.biojava.bio.seq.RNATools;

import org.slf4j.Logger;

/**
 * Convert bdg-formats Alphabet to Biojava 1.x Alphabet.
 *
 * @author  Michael Heuer
 */
@Immutable
final class BdgenomicsAlphabetToBiojavaAlphabet extends AbstractConverter<org.bdgenomics.formats.avro.Alphabet, org.biojava.bio.symbol.Alphabet> {

    /**
     * Package private no-arg constructor.
     */
    BdgenomicsAlphabetToBiojavaAlphabet() {
        super(org.bdgenomics.formats.avro.Alphabet.class, org.biojava.bio.symbol.Alphabet.class);
    }


    @Override
    public org.biojava.bio.symbol.Alphabet convert(final org.bdgenomics.formats.avro.Alphabet alphabet,
                                                   final ConversionStringency stringency,
                                                   final Logger logger) throws ConversionException {

        if (alphabet == null) {
            warnOrThrow(alphabet, "must not be null", null, stringency, logger);
            return null;
        }
        switch (alphabet) {
        case DNA:
            return DNATools.getDNA();
        case RNA:
            return RNATools.getRNA();
        case PROTEIN:
            return ProteinTools.getAlphabet();            
        }
        return null;
    }
}
