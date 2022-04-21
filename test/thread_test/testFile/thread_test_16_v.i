typedef unsigned long pthread_t;
typedef unsigned long pthread_mutex_t;
extern void pthread_mutex_lock(pthread_mutex_t* t);
extern void pthread_mutex_unlock(pthread_mutex_t* t);

int b = 0, c = 3;

pthread_t t1, t2;
pthread_mutex_t l1;

void *thread1(void *arg) {
 int a = 0;

 pthread_mutex_lock(&l1);
 if (c == 4) {
  pthread_mutex_unlock(&l1);
  b = 7;
 }
}

void *thread2(void *arg) {
 pthread_mutex_lock(&l1);
 c++;
 pthread_mutex_unlock(&l1);
 b = 8;
}

void main() {

 pthread_create(&t2, 
                    ((void *)0)
                        , thread2, 
                                   ((void *)0)
                                       );

 pthread_create(&t1, 
                    ((void *)0)
                        , thread1, 
                                   ((void *)0)
                                       );

 return;
}