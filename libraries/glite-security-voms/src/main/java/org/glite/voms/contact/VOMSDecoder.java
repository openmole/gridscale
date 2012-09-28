/*********************************************************************
 *
 * Authors: 
 *      
 *      Gidon Moont - g.moont@imperial.ac.uk  
 *          
 * Copyright (c) 2002, 2003, 2004, 2005, 2006 INFN-CNAF on behalf 
 * of the EGEE project.
 * 
 * For license conditions see LICENSE
 *
 * Parts of this code may be based upon or even include verbatim pieces,
 * originally written by other people, in which case the original header
 * follows.
 *
 *********************************************************************/
package org.glite.voms.contact;
// Gidon Moont
// Imperial College London
// Copyright (C) April 2006

// Voms uses a non-standard Base-64 algorithm.  Hmmm...

/**
 * 
 * This class implements a decoder for the non-standard Base-64 algorithm used
 * by voms.
 * 
 *  
 * @author Gidon Moont
 *
 */
public class VOMSDecoder {

  // matrix out of src/common/xml.c
  private static int[] decodemapint = new int[]
                          { 0,   0,  0,  0,  0,  0,  0,  0,
                            0,   0,  0,  0,  0,  0,  0,  0,
                            0,   0,  0,  0,  0,  0,  0,  0,
                            0,   0,  0,  0,  0,  0,  0,  0,
                            0,   0,  0,  0,  0,  0,  0,  0,
                            0,   0,  0,  0,  0,  0,  0,  0,
                            52, 53, 54, 55, 56, 57, 58, 59,
                            60, 61,  0,  0,  0,  0,  0,  0,
                            0,  26, 27, 28, 29, 30, 31, 32,
                            33, 34, 35, 36, 37, 38, 39, 40,
                            41, 42, 43, 44, 45, 46, 47, 48,
                            49, 50, 51, 62,  0, 63,  0,  0,
                            0,   0,  1,  2,  3,  4,  5,  6,
                            7,   8,  9, 10, 11, 12, 13, 14,
                            15, 16, 17, 18, 19, 20, 21, 22,
                            23, 24, 25,  0,  0,  0,  0,  0 } ;

  private static byte[] decodemapbyte = new byte[128] ;
  static
  {
    for( int i = 0 ; i < 128 ; i++ )
    {
      decodemapbyte[i] = (byte)decodemapint[i] ;
    }
  }

  public static byte[] decode( String s )
  {

    char[] in = s.toCharArray() ;
 
    int iLen = in.length ;

    // cuts off end - do I need this?
    // while (iLen > 0 && in[iLen-1] == '=') iLen--;

    int oLen = (iLen*3) / 4 ;
    byte[] out = new byte[oLen] ;
    int ip = 0;
    int op = 0;
    while( ip < iLen )
    {
      int i0 = in[ip++];
      int i1 = in[ip++];
      int i2 = ip < iLen ? in[ip++] : 'A';
      int i3 = ip < iLen ? in[ip++] : 'A';

      if (i0 > 127 || i1 > 127 || i2 > 127 || i3 > 127)
         throw new IllegalArgumentException ("Illegal character in Base64 encoded data.");

      int b0 = decodemapbyte[i0];
      int b1 = decodemapbyte[i1];
      int b2 = decodemapbyte[i2];
      int b3 = decodemapbyte[i3];
      if (b0 < 0 || b1 < 0 || b2 < 0 || b3 < 0)
         throw new IllegalArgumentException ("Illegal character in Base64 encoded data.");

      // ????????????
      int o0 = ( b0       <<2) | (b1>>>4);
      int o1 = ((b1 & 0xf)<<4) | (b2>>>2);
      int o2 = ((b2 &   3)<<6) |  b3;

      out[op++] = (byte)o0;
      if (op<oLen) out[op++] = (byte)o1;
      if (op<oLen) out[op++] = (byte)o2;

    }

    return out ;

  }

}
