GridScale
=========

GridScale is a scala library for accessing various file and batch systems. For the time being it supports:
* Remote SSH servers,
* PBS clusters,
* SLURM clusters,
* SGE clusters,
* OAR clusters,
* Condor flocks
* HTTP file lists,
* IPFS nodes,
* [EGI computing grid](https://www.egi.eu/) via DIRAC pilot jobs system.

Licence
-------
GridScale is licensed under the [GNU Affero GPLv3](LICENSE.md) software license.

Build
-------
GridScale builds with `sbt`.  
Use the `compile` and/or `package` task to build all the modules.  

Imports
-------
In order to use gridscale you should import the namespace corresponding to the job system you want to use:

    import gridscale.pbs._


SBT 
-------------
GridScale is cross compiled against serveral versions of scala. To use on of its modules add a dependency like:

    libraryDependencies += "fr.iscpif.gridscale" %% "pbs" % version

Examples
--------
Up to date examples are available in the [example directory](examples/).

Development
--------
GridScale can be generated locally using:
`sbt publish-local`

To release in one step, use:
`sbt 'release with-defaults'`
