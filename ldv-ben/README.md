## Direcory overview 

The directory contains the following parts:

* directories with tasks: [Safes](Safes), [Unsafes](Unsafes) and [Unknowns](Unknowns);
* a set of tasks [Tasks.set](Tasks.set);
* the [readme](README.md) file;
* a set of `*.xml` files with configurations.

## Tasks description

The tasks are based on modules in the directory `drivers/net` of Linux kernel v4.2.6.
An actual module is encoded into the task name, so, for example, `u__linux-concurrency_safety__drivers---net---caif---caif_serial.ko.cil.i` corresponds to `drivers/net/caif/caif_serial.ko`.

The verification tasks are prepared by [Klever](https://github.com/ldv-klever/klever) 2.1.dev160+g4df016cf8, linux:concurrency safety rule.
Build bases are collected by [Clade](https://github.com/17451k/clade) v3.0.2.

Thread creation is modeled by a function `pthread_create` with default signature, which creates a single thread, or `pthread_create_N`, which creates several instances at once.
Locking is organized by functions `ldv_mutex_model_lock`/`ldv_mutex_model_unlock` and `ldv_spin_model_lock`/`ldv_spin_model_unlock`.

## Configuration descriptions

InterAVT configuration is defined in [benchmark-AVT.xml](benchmark-AVT.xml), use `CPALockator-theory-with-races@r32609` branch.

ANT configurations are defined by [benchmark-ANT.xml](benchmark-ANT.xml), use `CPALockator-combat-mode@32792` branch

## Launches

Download CPAchecker [repository](https://svn.sosy-lab.org/software/cpachecker/trunk) and checkout to the specified branch.
To launch CPALockator execute:
```
./scripts/benchmark.py <benchmark>.xml
```