# SRD viscosity demo - rigid box particles

units		lj
atom_style	sphere
atom_modify	map array first big
dimension	2

# read in clusters of rigid bodies

fix             molprop all property/atom mol
read_data	data.box fix molprop NULL Molecules

set		type 1 mass 1.0
group		big type 1
velocity	big create 1.44 87287 loop geom
	
# equilibrate big particles

pair_style	soft 1.12
pair_coeff	1 1 0.0 
pair_coeff	2 2 0.0 0.0
pair_coeff	1 2 0.0 0.0

variable        prefactor equal ramp(0,60)
fix             soft all adapt 1 pair soft a * * v_prefactor

neighbor	0.3 bin
neigh_modify	delay 0 every 1 check yes

fix		1 big rigid molecule
fix		2 all enforce2d

#dump		1 all atom 10 dump.box.equil

compute		tbig all temp/sphere
thermo_modify	temp tbig

thermo		100
run		1000

#undump		1
unfix           soft  
unfix		1
unfix		2

# add small particles as hi density lattice

region		plane block INF INF INF INF -0.001 0.001 units box
lattice		sq 85.0
create_atoms	2 region plane

set		type 2 mass 0.1
group		small type 2
velocity	small create 1.0 593849 loop geom

# delete overlaps
# must set 1-2 cutoff to non-zero value

pair_style	lj/cut 2.5
pair_coeff	1 1 1.0 1.0
pair_coeff	2 2 0.0 1.0 0.0
pair_coeff	1 2 0.0 1.0 1.0

delete_atoms	overlap 1.0 small big

# SRD run

reset_timestep	0

neighbor	0.3 bin
neigh_modify	delay 0 every 1 check yes

comm_modify	mode multi group big vel yes
neigh_modify	include big

# no pairwise interactions with small particles

pair_style	lj/cut 2.5
pair_coeff	1 1 1.0 1.0
pair_coeff	2 2 0.0 1.0 0.0
pair_coeff	1 2 0.0 1.0 0.0

# use fix SRD to push small particles out from inside big ones
# if comment out, big particles won't see SRD particles

timestep	0.001

fix		1 big rigid molecule
fix	        2 small srd 20 big 1.0 0.25 49894 shift yes 54979 &
		  search 0.2 collision slip inside ignore overlap yes
fix		3 small viscosity 10 x y 50
fix		4 all enforce2d

# diagnostics

uncompute       tbig
compute		tbig big temp/sphere
variable	pebig equal pe*atoms/count(big)
variable	ebig equal etotal*atoms/count(big)
thermo_style	custom step temp f_2[8] etotal v_pebig v_ebig press &
		f_2[1] f_2[2] f_2[3] f_2[4] f_2[5] &
		f_2[6] f_2[7] f_2[8] f_2[9] f_2[10] f_2[11] f_2[12]

thermo_modify	temp tbig
thermo		1000

#dump		1 all atom 1000 dump.box.mp

#dump		1 all image 1000 image.*.jpg type type zoom 1.6
#dump_modify	1 pad 6 adiam 1 1 adiam 2 0.2

run		1000

