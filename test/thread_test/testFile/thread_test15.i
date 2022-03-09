# 1 "thread_test15.c"
# 1 "<built-in>"
# 1 "<command-line>"
# 31 "<command-line>"
# 1 "/usr/include/stdc-predef.h" 1 3 4
# 32 "<command-line>" 2
# 1 "thread_test15.c"






int b = 0;

void *thread1() {
 int a = 0;
 while(a < 3) {
  if (a > 1) {
   b = 2;
  }
  a++;
 }
}

void main() {
 b = 1;

 thread1();

 b = 3;

 return;
}
