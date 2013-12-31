Java Game Boy Advance
=================

This (when complete) will be a Java GBA Emulator utilizing Open GL. While I will strive for accuracy, such adjustments may be made down the road.


Todo list (in a somewhat particular order):
* Cycle counting
* Memory dispatching
* BIOS/SWI implementation
* Graphics/Audio implementation

Accuracy problems:
* Edge case for STM is not implemented - (Store OLD base if Rb is FIRST entry in Rlist, otherwise store NEW base)
* Handle empty STM/LDM register lists properly (ARMv4: R15 loaded/stored and Rb=Rb+/-40h)