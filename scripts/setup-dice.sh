
# Usage: source this file from your .bashrc
# www.brianziman.com


# Usage: roll [[<N>] <D>]
# Rolls NdD. If N isn't specified, rolls 1dD. If neither, then 1d20.
function roll() {
  NUMBER=1
  SIDES=20
  if [ "$#" = "2" ]; then
    NUMBER=$1
    shift
  fi
  if [ "$#" = "1" ]; then
    SIDES=$1
    shift
  fi
  echo "Rolling ${NUMBER}d${SIDES} (expected $[NUMBER * (1 + SIDES) / 2])"
  if [ $NUMBER = 1 ]; then
    echo "Rolled $[RANDOM % $SIDES + 1]"
  else
    SUM=0
    for i in `seq $NUMBER`; do
      VAL=$[RANDOM % $SIDES + 1]
      echo $VAL
      SUM=$[SUM + $VAL]
    done
    echo "Rolled $SUM"
  fi
}

# Set up dice rolling aliases...
# d4, d6, d8, d10, d12, d20, d100
# and 1dN through 14dN

for i in "" `seq 14`; do 
  for j in 4 6 8 10 12 20 100; do
    alias ${i}d${j}="roll $i $j"
  done
done







