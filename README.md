GridScale
=========

GridScale is a scala library for accessing various file and batch system. For the time being it supports:
* Glite / EMI, the European grid middleware,
* Remote SSH server,
* PBS clusters,
* SLURM clusters,
* SGE clusters,
* OAR clusters,
* Condor flocks
* HTTP file lists,
* DIRAC job pilot system.

Licence
-------
GridScale is licenced under the GNU Affero GPLv3 software licence. 

Build
-------
GridScale builds with `sbt`.  
Use the `compile` and/or `package` task to build all the modules.  

Imports
-------
In order to use gridscale you should import the folowing namespaces:

    import fr.iscpif.gridscale._


SBT 
-------------
GridScale is cross compiled against serveral versions of scala. To use on of its modules add a dependency like:

    libraryDependencies += "fr.iscpif.gridscale" %% "gridscalepbs" % version

Examples
--------
Up to date examples are available in the [example directory](examples/README.md).  
Standalone runnable jars can be generated for each example with the `one-jar` task (please note that this can only be done after the task `package` has been run.

