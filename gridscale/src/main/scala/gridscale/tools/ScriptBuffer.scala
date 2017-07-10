/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package gridscale.tools

class ScriptBuffer {
  var script = ""
  val EOL = "\n"

  def +=(s: String) { script += s + EOL }

  override def toString = script
}
