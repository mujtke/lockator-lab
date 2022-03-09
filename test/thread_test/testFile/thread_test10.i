# 1 "thread_test10.c"
# 1 "<built-in>"
# 1 "<command-line>"
# 31 "<command-line>"
# 1 "/usr/include/stdc-predef.h" 1 3 4
# 32 "<command-line>" 2
# 1 "thread_test10.c"

int sum(int m, int n) {
 if (m == 0)
  return n;
 else
 {
  int tmp = sum(m-1, n+1);
  return tmp;
 }
}

void main(void){

 int a = 4;
 int b = 3;
 int result = sum(a, b);

 return;
}
