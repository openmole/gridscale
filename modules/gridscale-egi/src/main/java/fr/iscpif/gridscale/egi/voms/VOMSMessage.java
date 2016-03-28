/**
 * Copyright (c) Istituto Nazionale di Fisica Nucleare, 2006-2014.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package fr.iscpif.gridscale.egi.voms;

/**
 * 
 * This class is used to decode VOMS error messages contained in a VOMS
 * response.
 * 
 * @author Andrea CEccanti
 * 
 */
public class VOMSMessage {

  int code;
  String message;

  public int getCode() {

    return code;
  }

  public void setCode(int code) {

    this.code = code;
  }

  public String getMessage() {

    return message;
  }

  public void setMessage(String message) {

    this.message = message;
  }

  public VOMSMessage(int code, String message) {

    this.code = code;
    this.message = message;
  }

  public String toString() {

    return "voms message " + code + ": " + message;
  }
}
